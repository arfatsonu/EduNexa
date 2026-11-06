package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.reports.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun ParentReportScreen(session: PortalSession, studentId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repo = remember(session.uid) { ParentReportRepository(session) }
    val context = LocalContext.current
    var data by remember(studentId) { mutableStateOf<ParentReportData?>(null) }
    var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var retry by remember { mutableStateOf(0) }
    var sessionId by rememberSaveable(studentId) { mutableStateOf("") }; var month by rememberSaveable(studentId) { mutableStateOf(FeePolicy.month()) }; var filters by remember { mutableStateOf(false) }; var wholeSessionTests by rememberSaveable(studentId) { mutableStateOf(false) }
    BackHandler { onBack() }
    LaunchedEffect(session.uid,studentId,retry) {
        loading=true;error=null
        try {
            val loaded=repo.load(studentId);data=loaded
            if(sessionId.isBlank()) {
                val enrolled=loaded.student.text("academicSessionId").ifBlank { loaded.student.text("academicSession") }
                sessionId=loaded.sessions.firstOrNull { it["id"]==enrolled || it["name"]==enrolled }?.get("id")?.toString().orEmpty().ifBlank { loaded.sessions.firstOrNull { it["isCurrent"]==true }?.get("id")?.toString().orEmpty() }
            }
        } catch(e: Exception) { if(e is CancellationException)throw e;error=e.message ?: "Could not load the complete report." }
        finally { loading=false }
    }
    val current=data
    val result=remember(current,sessionId,month,wholeSessionTests) { current?.let { runCatching { ParentReportPolicy.build(it,sessionId,month,wholeSessionTests) } } }
    val report=result?.getOrNull()
    Column(modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Outlined.ArrowBack,"Back to Students") }
            Text("Complete Student Report",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            IconButton(onClick={retry+=1},enabled=!loading) { Icon(Icons.Outlined.Refresh,"Refresh report") }
            IconButton(onClick={if(report!=null && current!=null) ParentReportPrinter.print(context,current.schoolName,report,session.schoolId)},enabled=report!=null && !loading && error==null) { Icon(Icons.Outlined.Print,"Print / Save PDF") }
        }
        if(loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        else if(error!=null) { PortalNotice(error!!,true);TextButton(onClick={retry+=1}) { Text("Retry") } }
        else if(current!=null) {
            ParentCard {
                Text(current.student.text("fullName"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(report?.scope ?: "Select an academic session",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=Color(0xFF64748B))
                    TextButton(onClick={filters=true}) { Text("Filters") }
                }
            }
            if(report==null) PortalNotice(result?.exceptionOrNull()?.message ?: "Select a session.",true)
            else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item { Text(report.details,style=MaterialTheme.typography.bodySmall,color=Color(0xFF64748B)) }
                report.sections.forEach { section ->
                    item {
                        ParentCard {
                            Text(section.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                            section.metrics.chunked(2).forEach { metrics -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { metrics.forEach { (label,value) -> Surface(Modifier.weight(1f),shape=RoundedCornerShape(10.dp),color=Color(0xFFF1F5F9)) { Column(Modifier.padding(10.dp)) { Text(label,style=MaterialTheme.typography.labelSmall,color=Color(0xFF64748B));Text(value,fontWeight=FontWeight.Bold) } } };if(metrics.size==1)Spacer(Modifier.weight(1f)) } }
                            Text(section.note,style=MaterialTheme.typography.bodySmall,color=Color(0xFF64748B))
                            if(section.rows.isEmpty()) Text("No saved records in this selection.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(section.rows) { row -> ParentCard {
                        Text(row.take(2).filter { it.isNotBlank() }.joinToString(" • "),fontWeight=FontWeight.Bold)
                        section.headers.zip(row).drop(2).filter { it.second.isNotBlank() }.forEach { (label,value) -> Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Text(label,Modifier.weight(0.35f),style=MaterialTheme.typography.bodySmall,color=Color(0xFF64748B));Text(value,Modifier.weight(0.65f),style=MaterialTheme.typography.bodySmall) } }
                    } }
                }
                item { Button(onClick={ParentReportPrinter.print(context,current.schoolName,report,session.schoolId)},modifier=Modifier.fillMaxWidth()) { Text("Print / Save Complete Report") } }
            }
            if(filters) {
                val cal=Calendar.getInstance(TimeZone.getTimeZone("Asia/Karachi"));val formatter=SimpleDateFormat("MMMM yyyy",Locale.US).apply { timeZone=cal.timeZone }
                val recent=(0..11).map { n -> formatter.format((cal.clone() as Calendar).apply { add(Calendar.MONTH,-n) }.time) }
                val saved=(current.attendance.map { it.text("attendanceDate").ifBlank { it.text("date") } } + current.diaries.map { it.text("date") } + current.tests.map { it.text("date") }).map { ReportPolicy.dateMonth(it) }.filter { it.isNotBlank() }
                val months=(listOf(month)+recent+saved).distinct().sortedByDescending { runCatching { FeePolicy.monthKey(it) }.getOrDefault("") }
                AlertDialog(onDismissRequest={filters=false},title={Text("Report Period")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    PortalSelect("Academic Session",sessionId,current.sessions.map { it["id"].toString() to it["name"].toString() },true) { sessionId=it }
                    PortalSelect("Month",month,months.map { it to it },true) { month=it }
                    PortalSelect("Test Period",if(wholeSessionTests) "session" else "month",listOf("session" to "Whole Academic Session", "month" to "Selected Month"),true) { wholeSessionTests=it=="session" }
                    Text("Attendance and homework use the selected month. Test results use the test period selected above.",style=MaterialTheme.typography.bodySmall)
                }},confirmButton={TextButton(onClick={filters=false}) { Text("Done") }})
            }
        }
    }
}
@Composable private fun ParentCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color(0xFFE2E8F0)),colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content) }
}
