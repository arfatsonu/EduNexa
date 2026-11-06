package pk.edunexa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.operations.awaitOperation
import pk.edunexa.app.settings.SchoolSettingsRepository
import pk.edunexa.app.workflow.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

@Composable
fun SchoolSetupGuide(session:PortalSession,teacherCount:Int,studentCount:Int,onNavigate:(String)->Unit,modifier:Modifier=Modifier) {
    var steps by remember{mutableStateOf<List<SetupStep>>(emptyList())};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)};var retry by remember{mutableIntStateOf(0)}
    LaunchedEffect(session.uid,session.schoolId,retry,teacherCount,studentCount){loading=true;error=null;try{
        val settings=SchoolSettingsRepository(session).load()
        val config=FirebaseFirestore.getInstance().collection("schools").document(session.schoolId).collection("academicConfig").document("current").get(Source.SERVER).awaitOperation().data.orEmpty()
        steps=WorkflowPolicy.setup(settings,config,teacherCount,studentCount)
    }catch(e:Exception){if(e is CancellationException)throw e;error=e.message}finally{loading=false}}
    Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("School Setup",style=MaterialTheme.typography.titleLarge)
        Text("Set up the real school in order. Your saved progress is checked each time you return.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={retry++},enabled=!loading){Text("Refresh checklist")}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{PortalNotice(it,true)}
        if(!loading&&error==null) {
            Text("${steps.count{it.complete}} / ${steps.size} steps ready",style=MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress={steps.count{it.complete}.toFloat()/steps.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){itemsIndexed(steps){index,step->PriorityAction("${index+1}. ${step.title}",step.detail,if(step.complete)"Review ✓" else "Set up",{onNavigate(step.destination)})}}
            OutlinedButton(onClick={onNavigate("Excel Import")},modifier=Modifier.fillMaxWidth()){Text("Import students / staff from Excel")}
        }
    }
}
