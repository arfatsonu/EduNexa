package pk.edunexa.app.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.edunexa.app.diary.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DiaryGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val DiaryMuted = Color(0xFF64748B)
private fun todayDiary() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun Map<String, Any?>.label(key: String) = this[key]?.toString().orEmpty()

@Composable
fun AdminDiaryScreen(schoolId: String, adminName: String, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val repository = remember { DiaryRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var data by remember { mutableStateOf<DiaryData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(notice) { notice = null }
    var busy by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("Diary") }
    var sessionId by rememberSaveable { mutableStateOf("") }
    var classId by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayDiary()) }
    var query by rememberSaveable { mutableStateOf("") }
    var viewing by remember { mutableStateOf<DiaryItem?>(null) }
    LaunchedEffect(schoolId, refresh) {
        loading = true
        runCatching { repository.load(schoolId) }.onSuccess { data = it; error = null; if (sessionId.isBlank()) sessionId = it.currentSession.label("id") }.onFailure { error = it.message ?: "Diary load failed." }
        loading = false
    }
    BackHandler(enabled = viewing != null) { viewing = null }
    val current = data
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        if (viewing != null && current != null) {
            HomeworkViewer(viewing!!, current, onBack = { viewing = null })
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diary & Homework", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Current session: ${current?.currentSession?.label("name").orEmpty()}", color = DiaryGold, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { refresh += 1; error = null }, enabled = !loading && !busy) { Icon(Icons.Outlined.Refresh, "Refresh diary") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Diary", "Homework").forEach { name -> FilterChip(tab == name, { tab = name; error = null }, label = { Text(name) }, enabled = !busy) } }
            notice?.let { Text(it, color = Color(0xFF15803D), modifier = Modifier.padding(vertical = 6.dp)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refresh += 1; error = null }, enabled = !busy) { Text("Retry / Refresh") } }
            if (loading) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (current != null) {
                DiarySelect("Session", sessionId, current.options("sessions").map { it.label("id") to it.label("name") }, !busy) { sessionId = it }
                DiarySelect("Class", classId, listOf("" to "All Classes") + current.options("classes").map { it.label("id") to it.label("name") }, !busy) { classId = it }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DiaryDate("Date", date, !busy, Modifier.weight(1f)) { date = it }
                    TextButton(onClick = { date = "" }, enabled = !busy) { Text("All Dates") }

                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search topic or subject") })
                val visible = current.entries.filter { entry ->
                    (entry.text("academicSessionId") == sessionId || entry.text("academicSession") == current.options("sessions").firstOrNull { it["id"] == sessionId }?.label("name")) &&
                        (classId.isBlank() || entry.text("classId") == classId) && (date.isBlank() || entry.text("date") == date) &&
                        (entry.text("topic").contains(query.trim(), true) || entry.text("subject").contains(query.trim(), true)) && (tab == "Diary" || entry.text("homework").isNotBlank())
                }
                Text("${visible.size} ${if (tab == "Diary") "diary entries" else "homework tasks"}", color = DiaryMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (visible.isEmpty()) item { Text("No entries in this selection.", color = DiaryMuted, modifier = Modifier.padding(20.dp)) }
                    items(visible, key = { it.id }) { entry ->
                        val editable = current.options("sessions").any { it["id"] == entry.text("academicSessionId") && it["status"] in setOf("Active", "Upcoming") }
                        DiaryCard {
                            Text("${entry.text("className")} • ${entry.text("section")} • ${entry.text("subject")}", color = DiaryGold, style = MaterialTheme.typography.labelLarge)
                            Text(entry.text("topic"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${entry.text("date")} • ${entry.text("teacherName")}", color = DiaryMuted, style = MaterialTheme.typography.bodySmall)
                            if (entry.text("classWork").isNotBlank() && tab == "Diary") Text("Classwork: ${entry.text("classWork")}")
                            if (entry.text("homework").isNotBlank()) { Text("Homework: ${entry.text("homework")}"); Text("Due: ${entry.text("dueDate")}", color = DiaryMuted, style = MaterialTheme.typography.bodySmall) }
                            if (entry.text("teacherNote").isNotBlank()) Text("Note: ${entry.text("teacherNote")}", color = DiaryMuted)
                            var previewAttachment by remember { mutableStateOf<Map<String, Any?>?>(null) }
                            previewAttachment?.let { AttachmentPreviewDialog(it) { previewAttachment = null } }
                            if (entry.attachment.isNotEmpty()) TextButton(onClick = {
                                val url = entry.attachment.label("url")
                                if (url.startsWith("https://")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { error = "A compatible app is required to open this attachment." }
                                else if (url.startsWith("data:")) previewAttachment = entry.attachment
                                else error = "The attachment download link is unavailable."
                            }) { Text("Open ${entry.attachment.label("name")}") }
                            if (tab == "Homework") {
                                val records = DiaryPolicy.maps(current.checks.firstOrNull { it.text("diaryId") == entry.id }?.raw?.get("records"))
                                Text("${records.count { it["status"] == "Completed" }} completed • ${records.count { it["status"] != "Not Checked" }} checked", color = DiaryMuted, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { viewing = entry; error = null }, enabled = records.isNotEmpty()) { Text("View Homework Checks") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkViewer(entry: DiaryItem, data: DiaryData, onBack: () -> Unit) {
    val check = data.checks.firstOrNull { it.text("diaryId") == entry.id }
    val records = DiaryPolicy.maps(check?.raw?.get("records"))
    Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
            Text("Homework Checks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text("${entry.text("className")} • ${entry.text("section")} • ${entry.text("subject")}", color = DiaryGold)
        Text("Checked by: ${check?.text("checkedBy").orEmpty()}", color = DiaryMuted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            if (records.isEmpty()) item { Text("No saved homework checks.") }
            items(records) { record -> DiaryCard {
                Text(record.label("studentName"), fontWeight = FontWeight.Bold)
                Text("Roll: ${record.label("rollNo")} • ${record.label("admissionNo")}", color = DiaryMuted)
                Text(record.label("status"), color = DiaryGold)
                if (record.label("remarks").isNotBlank()) Text(record.label("remarks"))
            } }
        }
    }
}

@Composable
private fun DiaryCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}
@Composable
private fun DiarySelect(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = DiaryMuted, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text(options.firstOrNull { it.first == value }?.second ?: "Select $label", Modifier.weight(1f)); Text("▾") }
            DropdownMenu(expanded && enabled, { expanded = false }) { options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) }) } }
        }
    }
}
@Composable
private fun DiaryDate(label: String, value: String, enabled: Boolean, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    TextButton(onClick = {
        val cal = Calendar.getInstance()
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)?.let { cal.time = it } }
        DatePickerDialog(context, { _, y, m, d -> onSelect(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }, enabled = enabled, modifier = modifier) { Text("$label: ${value.ifBlank { "All" }}") }
}


