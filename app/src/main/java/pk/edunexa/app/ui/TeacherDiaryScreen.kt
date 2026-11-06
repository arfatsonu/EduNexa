package pk.edunexa.app.ui
import android.graphics.BitmapFactory
import android.graphics.Bitmap

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
import pk.edunexa.app.auth.PortalSession
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DiaryGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val DiaryMuted = Color(0xFF64748B)
private fun todayDiary() = pk.edunexa.app.fees.FeePolicy.today()
private fun Map<String, Any?>.label(key: String) = this[key]?.toString().orEmpty()

@Composable
fun TeacherDiaryScreen(session: PortalSession, modifier: Modifier = Modifier) {
    val schoolId = session.schoolId
    val adminName = session.name
    val onSaved: () -> Unit = {}
    val repository = remember(session.uid) { TeacherDiaryRepository(session) }
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
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var classId by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayDiary()) }
    var query by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<DiaryItem?>(null) }
    var checking by remember { mutableStateOf<DiaryItem?>(null) }
    var deleting by remember { mutableStateOf<DiaryItem?>(null) }
    var previewAttachment by remember { mutableStateOf<Map<String, Any?>?>(null) }
    previewAttachment?.let { AttachmentPreviewDialog(it) { previewAttachment = null } }
    LaunchedEffect(schoolId, refresh) {
        loading = true
        runCatching { repository.load(schoolId) }.onSuccess { data = it; error = null; if (sessionId.isBlank()) sessionId = it.currentSession.label("id") }.onFailure { error = it.message ?: "Diary load failed." }
        loading = false
    }
    fun complete(message: String) { busy = false; editor = null; checking = null; deleting = null; error = null; notice = message; refresh += 1; onSaved() }
    BackHandler(enabled = editor != null || checking != null) { if (!busy) { editor = null; checking = null; error = null } }
    val current = data
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        if (editor != null && current != null) {
            DiaryEditor(editor!!, current, busy, error, onCancel = { editor = null; error = null }, onSave = { draft, file ->
                busy = true; error = null
                scope.launch { runCatching { repository.save(schoolId, draft, file, adminName) }.onSuccess { complete("Diary saved.") }.onFailure { busy = false; error = it.message } }
            })
        } else if (checking != null && current != null) {
            val entry = checking!!
            val old = current.checks.firstOrNull { it.text("diaryId") == entry.id }
            HomeworkEditor(entry, current, old, busy, error, readOnly = entry.text("teacherId") != session.profileId || !TeacherDiaryPolicy.canWrite(current.config, entry, session.profileId.orEmpty()), onCancel = { checking = null; error = null }, onSave = { records ->
                busy = true; error = null
                scope.launch { runCatching { repository.checkHomework(schoolId, entry, records, old, adminName) }.onSuccess { complete("Homework checks saved.") }.onFailure { busy = false; error = it.message } }
            })
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diary & Homework", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Current session: ${current?.currentSession?.label("name").orEmpty()}", color = DiaryGold, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { refresh += 1; error = null }, enabled = !loading && !busy) { Icon(Icons.Outlined.Refresh, "Refresh diary") }
            }
            PortalTabs(listOf("Diary", "Homework"), tab, !busy) { tab = it; error = null }
            notice?.let { Text(it, color = Color(0xFF15803D), modifier = Modifier.padding(vertical = 6.dp)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refresh += 1; error = null }, enabled = !busy) { Text("Retry / Refresh") } }
            if (loading) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (current != null) {
                TextButton(onClick = { filtersExpanded = true }, enabled = !busy) { Text("Filters • " + current.options("classes").firstOrNull { it["id"] == classId }?.label("name").orEmpty().ifBlank { "All Assigned Classes" }) }
                if(filtersExpanded) AlertDialog(onDismissRequest = { filtersExpanded = false },title = { Text("Diary Filters") },text = { Column {
                DiarySelect("Session", sessionId, current.options("sessions").map { it.label("id") to it.label("name") }, !busy) { sessionId = it }
                DiarySelect("Class", classId, listOf("" to "All Classes") + current.options("classes").map { it.label("id") to it.label("name") }, !busy) { classId = it }
                } },confirmButton = { TextButton(onClick = { filtersExpanded = false }) { Text("Done") } })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DiaryDate("Date", date, !busy, Modifier.weight(1f)) { date = it }
                    TextButton(onClick = { date = "" }, enabled = !busy) { Text("All Dates") }
                    if (tab == "Diary") IconButton(onClick = {
                        error = null; notice = null
                        editor = DiaryItem(raw = mapOf("academicSessionId" to sessionId, "date" to date.ifBlank { todayDiary() }, "dueDate" to date.ifBlank { todayDiary() }, "classId" to classId))
                    }, enabled = !busy && current.options("sessions").any { it["id"] == sessionId && it["status"] in setOf("Active", "Upcoming") }) { Icon(Icons.Outlined.Add, "Add diary") }
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
                        val editable = entry.text("teacherId") == session.profileId && TeacherDiaryPolicy.canWrite(current.config, entry, session.profileId.orEmpty())
                        DiaryCard {
                            Text("${entry.text("className")} • ${entry.text("section")} • ${entry.text("subject")}", color = DiaryGold, style = MaterialTheme.typography.labelLarge)
                            Text(entry.text("topic"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${entry.text("date")} • ${entry.text("teacherName")}", color = DiaryMuted, style = MaterialTheme.typography.bodySmall)
                            if (entry.text("classWork").isNotBlank() && tab == "Diary") Text("Classwork: ${entry.text("classWork")}")
                            if (entry.text("homework").isNotBlank()) { Text("Homework: ${entry.text("homework")}"); Text("Due: ${entry.text("dueDate")}", color = DiaryMuted, style = MaterialTheme.typography.bodySmall) }
                            if (entry.text("teacherNote").isNotBlank()) Text("Note: ${entry.text("teacherNote")}", color = DiaryMuted)
                            if (entry.attachment.isNotEmpty()) TextButton(onClick = {
                                val url = entry.attachment.label("url")
                                if (url.startsWith("https://")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { error = "A compatible app is required to open this attachment." }
                                else if (url.startsWith("data:")) previewAttachment = entry.attachment
                                else error = "The attachment download link is unavailable."
                            }) { Text("Open ${entry.attachment.label("name")}") }
                            if (tab == "Diary") Row {
                                TextButton(onClick = { editor = entry; error = null }, enabled = !busy && editable) { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Text("Edit") }
                                TextButton(onClick = { deleting = entry; error = null }, enabled = !busy && editable) { Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Text("Delete") }
                            } else {
                                val records = DiaryPolicy.maps(current.checks.firstOrNull { it.text("diaryId") == entry.id }?.raw?.get("records"))
                                Text("${records.count { it["status"] == "Completed" }} completed • ${records.count { it["status"] != "Not Checked" }} checked", color = DiaryMuted, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { checking = entry; error = null }, enabled = !busy && (editable || records.isNotEmpty())) { Text(if (!editable) "View Checks" else if (records.isEmpty()) "Check Homework" else "View / Edit Checks") }
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { entry -> AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("Delete diary?") },
        text = { Column { Text("Delete ${entry.text("topic")}? A diary with saved homework checks cannot be deleted."); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(onClick = { busy = true; error = null; scope.launch { runCatching { repository.delete(schoolId, entry) }.onSuccess { complete("Diary deleted.") }.onFailure { busy = false; error = it.message } } }, enabled = !busy) { Text(if (busy) "Deleting…" else "Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null; error = null }, enabled = !busy) { Text("Cancel") } }) }
}

@Composable
private fun DiaryEditor(entry: DiaryItem, data: DiaryData, busy: Boolean, error: String?, onCancel: () -> Unit, onSave: (DiaryItem, PendingDiaryFile?) -> Unit) {
    var fields by remember(entry.id) { mutableStateOf(entry.raw) }
    var file by remember { mutableStateOf<PendingDiaryFile?>(null) }
    var fileBusy by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun field(key: String) = fields.label(key)
    fun change(key: String, value: String) { fields = fields + (key to value) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { fileBusy = true; fileError = null; scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "attachment" } ?: "attachment"
                val mime = resolver.getType(uri).orEmpty()
                val rawBytes = resolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n < 5 * 1024 * 1024) { "The file must be smaller than 5 MB." }; out.write(buffer, 0, n) }; out.toByteArray()
                } ?: error("Could not read the file.")
                val bytes = if (mime.startsWith("image/") && rawBytes.size > 350 * 1024) {
                    runCatching {
                        val orig = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                        if (orig != null) {
                            val ratio = minOf(1f, 1200f / maxOf(orig.width, orig.height))
                            val scaled = Bitmap.createScaledBitmap(orig, maxOf(1, (orig.width * ratio).toInt()), maxOf(1, (orig.height * ratio).toInt()), true)
                            val stream = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                            if (scaled !== orig) scaled.recycle()
                            orig.recycle()
                            stream.toByteArray()
                        } else rawBytes
                    }.getOrDefault(rawBytes)
                } else rawBytes
                PendingDiaryFile(name, mime, bytes).also { DiaryPolicy.validateFile(it) }
            } }.onSuccess { file = it }.onFailure { fileError = it.message }
            fileBusy = false
        } }
    }
    val enabled = !busy && !fileBusy
    val existing = entry.id.isNotBlank()
    val cls = field("classId"); val session = field("academicSessionId")
    val assigned = data.options("teacherAssignments")
    val subjects = data.options("subjects").filter { it["isActive"] == true && data.options("classSubjectAssignments").any { a -> a["classId"] == cls && a["subjectId"] == it["id"] && a["academicSession"] == session } && assigned.any { a -> a["classId"] == cls && a["sectionId"] == field("sectionId") && a["subjectId"] == it["id"] && a["academicSession"] == session } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onCancel, enabled = enabled) { Icon(Icons.Outlined.ArrowBack, "Back") }; Text(if (existing) "Edit Diary" else "Add Diary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        DiarySelect("Session", session, data.options("sessions").filter { it["status"] in setOf("Active", "Upcoming") }.map { it.label("id") to it.label("name") }, enabled && !existing) { change("academicSessionId", it); change("subjectId", "") }
        DiaryDate("Diary Date", field("date"), enabled && !existing) { change("date", it); if (field("dueDate") < it) change("dueDate", it) }
        if (existing) Text("${field("className")} • ${field("section")} • ${field("subject")}", color = DiaryGold)
        else {
            DiarySelect("Class", cls, data.options("classes").filter { it["isActive"] == true && assigned.any { a -> a["classId"] == it["id"] && a["academicSession"] == session } }.map { it.label("id") to it.label("name") }, enabled) { change("classId", it); change("sectionId", ""); change("subjectId", "") }
            DiarySelect("Section", field("sectionId"), data.options("sections").filter { it["classId"] == cls && it["isActive"] == true && assigned.any { a -> a["classId"] == cls && a["sectionId"] == it["id"] && a["academicSession"] == session } }.map { it.label("id") to it.label("name") }, enabled) { change("sectionId", it); change("subjectId", "") }
            DiarySelect("Subject", field("subjectId"), subjects.map { it.label("id") to it.label("name") }, enabled) { change("subjectId", it) }
            if (cls.isNotBlank() && subjects.isEmpty()) Text("Assign the class curriculum in Subjects first.", color = DiaryMuted)
        }
        listOf("topic" to "Topic", "classWork" to "Classwork", "homework" to "Homework", "teacherNote" to "Teacher Note").forEach { (key, title) ->
            OutlinedTextField(field(key), { change(key, it) }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(title) }, minLines = if (key == "topic") 1 else 2)
        }
        DiaryDate("Due Date", field("dueDate"), enabled) { change("dueDate", it) }
        Text(file?.name ?: (fields["attachment"] as? Map<*, *>)?.get("name")?.toString() ?: "No attachment", color = DiaryMuted)
        Row {
            OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }, enabled = enabled) { Text(if (fileBusy) "Reading…" else "Attach PDF / Image") }
            TextButton(onClick = { file = null; fields = fields + ("attachment" to null); fileError = null }, enabled = enabled) { Text("Remove") }
        }
        Text("Maximum attachment size: under 2 MB.", color = DiaryMuted, style = MaterialTheme.typography.bodySmall)
        (fileError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(DiaryItem(entry.id, fields), file) }, enabled = enabled && field("topic").isNotBlank() && field("classId").isNotBlank() && field("sectionId").isNotBlank() && field("subjectId").isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save Diary") }
        OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun HomeworkEditor(entry: DiaryItem, data: DiaryData, previous: DiaryItem?, busy: Boolean, error: String?, readOnly: Boolean = false, onCancel: () -> Unit, onSave: (List<Map<String, Any?>>) -> Unit) {
    val old = DiaryPolicy.maps(previous?.raw?.get("records"))
    var records by remember(entry.id) { mutableStateOf(run {
        val roster = data.roster(entry).map { student ->
            old.firstOrNull { it["studentId"] == student.id } ?: (entry.raw.filterKeys { it in setOf("classId", "className", "sectionId", "section", "subjectId", "subject", "academicSessionId", "academicSession", "date", "dueDate") } + mapOf("id" to "${entry.id}-${student.id}", "diaryId" to entry.id, "studentId" to student.id, "studentName" to student.text("fullName"), "rollNo" to student.text("rollNo"), "admissionNo" to student.text("admissionNo"), "homeworkTitle" to entry.text("homework"), "status" to "Not Checked", "remarks" to ""))
        }
        roster + old.filter { record -> roster.none { it["studentId"] == record["studentId"] } }
    }) }
    fun update(id: String, key: String, value: String) { records = records.map { if (it["studentId"] == id) it + (key to value) else it } }
    Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onCancel, enabled = !busy) { Icon(Icons.Outlined.ArrowBack, "Back") }; Text("Check Homework", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        Text("${entry.text("className")} • ${entry.text("section")} • ${entry.text("subject")}", color = DiaryGold)
        Text(entry.text("homework"), modifier = Modifier.padding(vertical = 6.dp))
        Text("${records.count { it["status"] != "Not Checked" }} / ${records.size} checked", color = DiaryMuted)
        if (records.isEmpty()) Text("No students linked to this class/section/session.", color = DiaryMuted)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(records, key = { it.label("studentId") }) { record -> DiaryCard {
                Text(record.label("studentName"), fontWeight = FontWeight.Bold)
                Text("Roll: ${record.label("rollNo")} • ${record.label("admissionNo")}", color = DiaryMuted, style = MaterialTheme.typography.bodySmall)
                DiarySelect("Status", record.label("status"), DiaryPolicy.statuses.map { it to it }, !busy && !readOnly) { update(record.label("studentId"), "status", it) }
                OutlinedTextField(record.label("remarks"), { update(record.label("studentId"), "remarks", it) }, Modifier.fillMaxWidth(), enabled = !busy && !readOnly, label = { Text("Remarks") })
            } }
        }
        if (!readOnly) Button(onClick = { onSave(records) }, enabled = !busy && records.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save Homework Checks") }
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



