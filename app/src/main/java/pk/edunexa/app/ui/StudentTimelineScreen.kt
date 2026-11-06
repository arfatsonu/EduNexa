package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.operations.SchoolOperationsRepository
import pk.edunexa.app.operations.awaitOperation
import pk.edunexa.app.workflow.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

@Composable
fun AdminStudentTimeline(session:PortalSession,studentId:String,onBack:()->Unit,modifier:Modifier=Modifier) {
    var events by remember(studentId){mutableStateOf<List<TimelineEvent>>(emptyList())};var name by remember{mutableStateOf("")};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)};var retry by remember{mutableIntStateOf(0)}
    BackHandler{onBack()}
    LaunchedEffect(session.uid,studentId,retry) {
        loading=true;error=null
        try {
            SchoolOperationsRepository(session).authorize()
            val root=FirebaseFirestore.getInstance().collection("schools").document(session.schoolId)
            val student=root.collection("students").document(studentId).get(Source.SERVER).awaitOperation();check(student.exists()){ "Student record was not found." }
            val pupil=DiaryItem(student.id,student.data.orEmpty());name=pupil.text("fullName")
            val rows=coroutineScope { listOf("attendance","fees","diaryEntries","publishedResults").map{collection->async{
                val query=if(collection=="diaryEntries")root.collection(collection).whereArrayContains("studentIds",studentId) else root.collection(collection).whereEqualTo("studentId",studentId)
                query.get(Source.SERVER).awaitOperation().documents.map{DiaryItem(it.id,it.data.orEmpty())}
            }}.map{it.await()} }
            events=TimelinePolicy.build(pupil,rows[0],rows[1],rows[2],rows[3])
        } catch(e:Exception){if(e is CancellationException)throw e;error=e.message}finally{loading=false}
    }
    Column(modifier.fillMaxSize().padding(horizontal=16.dp)) {
        TextButton(onClick=onBack){Text("Back to students")}
        Text(name.ifBlank{"Student timeline"},style=MaterialTheme.typography.titleLarge)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{PortalNotice(it,true);TextButton(onClick={retry++}){Text("Retry")}}
        if(!loading&&error==null)TimelineFeed(events,Modifier.weight(1f))
    }
}
@Composable
internal fun TimelineFeed(events:List<TimelineEvent>,modifier:Modifier=Modifier) {
    var type by rememberSaveable{mutableStateOf("All")};var query by rememberSaveable{mutableStateOf("")};var visible by remember{mutableIntStateOf(50)}
    val filtered=remember(events,type,query){events.filter{(type=="All"||it.type==type)&&listOf(it.title,it.detail,it.date).any{value->value.contains(query.trim(),true)}}}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Student Timeline",style=MaterialTheme.typography.titleMedium)
        Text("Saved activity across sessions. Unmarked attendance and unpublished results are excluded.",style=MaterialTheme.typography.bodySmall)
        PortalTabs(listOf("All","Attendance","Fees","Homework","Results","Admission"),type){type=it;visible=50}
        OutlinedTextField(query,{query=it;visible=50},Modifier.fillMaxWidth(),label={Text("Search activity or YYYY-MM")},singleLine=true)
        if(filtered.isEmpty())Text("No saved activity in this selection.")
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=16.dp)) {
            items(filtered.take(visible),key={it.id}){event->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text("${event.date} • ${event.type}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)
                Text(event.title,style=MaterialTheme.typography.titleSmall)
                if(event.detail.isNotBlank())Text(event.detail,style=MaterialTheme.typography.bodyMedium)
            }}}
            if(filtered.size>visible)item{OutlinedButton(onClick={visible+=50},modifier=Modifier.fillMaxWidth()){Text("Show more activity")}}
        }
    }
}
