package pk.edunexa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.reports.*
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun AdminReportsScreen(session: PortalSession, modifier: Modifier = Modifier) {
    val repo = remember(session.uid) { ReportRepository(session) }; val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf("Students") }
    var sessionId by rememberSaveable { mutableStateOf("") }; var classId by rememberSaveable { mutableStateOf("") }; var sectionId by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf(FeePolicy.month()) }; var query by rememberSaveable { mutableStateOf("") }; var filters by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var data by remember { mutableStateOf<ReportData?>(null) }
    LaunchedEffect(session.uid, category, refresh) {
        loading = true; error = null; data = null
        runCatching { repo.load(category) }.onSuccess { data = it; if(sessionId.isBlank()) sessionId = if(category == "Students") "__all__" else it.currentSession["id"]?.toString().orEmpty() }.onFailure { if(it is CancellationException) throw it; error = it.message ?: "Could not load the report." }
        loading = false
    }
    val current = data
    val filter = ReportFilter(sessionId, classId, sectionId, month, query)
    val result = remember(category, current, filter) { current?.let { runCatching { ReportPolicy.build(category, it, filter) } } }
    val document = result?.getOrNull()
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("School records & summaries", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B)) }
            IconButton(onClick = { refresh += 1 }, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Refresh report") }
            IconButton(onClick = { if(document != null && current != null) ReportPrinter.print(context, current.schoolName, document,session.schoolId) }, enabled = document != null && !loading && error == null) { Icon(Icons.Outlined.Print, "Print / Save PDF") }
        }
        PortalTabs(ReportPolicy.categories, category) { category = it; query = ""; sessionId = "" }
        if(loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if(error != null || result?.isFailure == true) {
            PortalNotice(error ?: result?.exceptionOrNull()?.message ?: "Report data could not be verified.", failure = true)
            TextButton(onClick = { refresh += 1 }) { Text("Retry") }
        } else if(document != null && current != null) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ReportCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(document.title, fontWeight = FontWeight.Bold); Text(document.scope, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B)) }
                            if(category != "Faculty") TextButton(onClick = { filters = true }) { Text("Filters") }
                        }
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text(if(category == "Exams") "Search title, subject or teacher" else "Search records") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        document.metrics.chunked(2).forEach { metrics -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { metrics.forEach { (label, value) ->
                            Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.primary) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(label, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelMedium); Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            }
                        }; if(metrics.size == 1) Spacer(Modifier.weight(1f)) } }
                    }
                }
                item { ReportCard { document.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B)) }; OutlinedButton(onClick = { ReportPrinter.print(context, current.schoolName, document,session.schoolId) }, modifier = Modifier.fillMaxWidth()) { Text("Print / Save PDF Report") } } }
                item { Text("${document.rows.size} saved records", style = MaterialTheme.typography.labelLarge, color = Color(0xFF64748B)) }
                if(document.rows.isEmpty()) item { PortalEmptyState(Icons.Outlined.BarChart, "No records in this selection", "Change the filters or search to view saved school records.") }
                itemsIndexed(document.rows) { _, row -> ReportCard {
                    Text(row.firstOrNull().orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    document.headers.zip(row).drop(1).forEach { (label, value) ->
                        if(value.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(label, Modifier.weight(0.4f), color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                            Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                } }
            }
            if(filters) AlertDialog(onDismissRequest = { filters = false }, title = { Text("Report Filters") }, confirmButton = { TextButton(onClick = { filters = false }) { Text("Done") } }, text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalSelect("Academic Session", sessionId, (if(category == "Students") listOf("__all__" to "All Sessions / All Students") else emptyList()) + current.options("sessions").map { it["id"].toString() to it["name"].toString() }, true) { sessionId = it }
                    PortalSelect("Class", classId, listOf("" to "All Classes") + current.options("classes").map { it["id"].toString() to it["name"].toString() }, true) { classId = it; sectionId = "" }
                    PortalSelect("Section", sectionId, listOf("" to "All Sections") + current.options("sections").filter { classId.isNotBlank() && it["classId"] == classId }.map { it["id"].toString() to it["name"].toString() }, true) { sectionId = it }
                    if(category in setOf("Attendance", "Fees", "Exams")) {
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Karachi"))
                        val formatter = SimpleDateFormat("MMMM yyyy", Locale.US).apply { timeZone = calendar.timeZone }
                        val recent = (0..11).map { n -> formatter.format((calendar.clone() as Calendar).apply { add(Calendar.MONTH, -n) }.time) }
                        val saved = current.records.map { if(category == "Fees") it.text("month") else ReportPolicy.dateMonth(it.text(if(category == "Attendance") "attendanceDate" else "date").ifBlank { it.text("date") }) }.filter { it.isNotBlank() }
                        PortalSelect("Month", month, listOf("" to "All Months") + (recent + saved).distinct().sortedByDescending { runCatching { FeePolicy.monthKey(it) }.getOrDefault("") }.map { it to it }, true) { month = it }
                    }
                    TextButton(onClick = { classId = ""; sectionId = ""; month = FeePolicy.month(); query = ""; sessionId = if(category == "Students") "__all__" else current.currentSession["id"]?.toString().orEmpty() }) { Text("Reset Filters") }
                }
            })
        }
    }
}
@Composable private fun ReportCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}



