package pk.edunexa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.operations.SchoolOperationsRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ActivityHistoryScreen(session:PortalSession,modifier:Modifier=Modifier) {
    val repo=remember(session.uid,session.schoolId){SchoolOperationsRepository(session)}
    var rows by remember {mutableStateOf<List<Map<String,Any?>>>(emptyList())};var error by remember{mutableStateOf<String?>(null)};var loading by remember{mutableStateOf(true)};var retry by remember{mutableIntStateOf(0)};var search by remember{mutableStateOf("")}
    LaunchedEffect(session.uid,session.schoolId,retry){loading=true;error=null;try{rows=repo.history()}catch(e:Exception){if(e is CancellationException)throw e;error=e.message}finally{loading=false}}
    Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Activity History",style=MaterialTheme.typography.titleLarge)
        Text("Attendance, fee receipts, profiles and school settings recorded from this update onward. Shows 250 most recently edited records per module, with up to 100 changes each; no passwords are stored.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("Search person, action or staff")},singleLine=true)
        TextButton(onClick={retry++},enabled=!loading){Text("Refresh")}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{PortalNotice(it,true)}
        if(!loading&&error==null) {
            val filtered=rows.filter{r->listOf("target","action","actorName","actorUid").any{r[it].toString().contains(search,true)}}
            if(filtered.isEmpty())Text("No recorded activity in this selection.")
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(filtered,key={it["id"].toString()}){r->
                Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(r["action"].toString(),style=MaterialTheme.typography.titleSmall)
                    Text(r["target"].toString())
                    Text("${r["actorName"]} • ${r["role"]}",style=MaterialTheme.typography.bodySmall)
                    val time=runCatching{Instant.parse(r["at"].toString()).atZone(ZoneId.of("Asia/Karachi")).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss 'PKT'"))}.getOrDefault(r["at"].toString())
                    Text(time,style=MaterialTheme.typography.bodySmall)
                    Text("Changed: "+(r["fields"] as? List<*>)?.joinToString().orEmpty(),style=MaterialTheme.typography.bodySmall)
                }}
            }}
        }
    }
}
