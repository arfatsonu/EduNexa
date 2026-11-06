package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.edunexa.app.academic.*
import java.util.Locale

private val SubjectGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val SubjectMuted = Color(0xFF64748B)

@Composable
fun AdminSubjectsScreen(schoolId: String, onConfigSaved: () -> Unit, modifier: Modifier = Modifier) {
    val repository = remember { SubjectRepository() }
    var data by remember(schoolId) { mutableStateOf<SubjectData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(notice) { notice = null }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf("directory") }
    var query by rememberSaveable { mutableStateOf("") }
    var classId by rememberSaveable { mutableStateOf("") }
    var sessionId by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<SchoolSubject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var deactivate by remember { mutableStateOf<SchoolSubject?>(null) }
    var discard by remember { mutableStateOf(false) }
    val current = data
    val assigned = current?.assigned(classId, sessionId).orEmpty()
    var selected by remember(schoolId, classId, sessionId, assigned) { mutableStateOf(assigned) }
    val dirty = selected != assigned
    LaunchedEffect(schoolId, refresh) {
        loading = true
        repository.load(schoolId) { result ->
            loading = false
            result.onSuccess { loaded ->
                data = loaded; error = null
                if (loaded.classes.none { it.id == classId }) classId = loaded.classes.firstOrNull { it.active }?.id.orEmpty()
                if (loaded.sessions.none { it.id == sessionId }) sessionId = loaded.sessions.firstOrNull { it.current }?.id.orEmpty()
            }.onFailure { error = it.message ?: "Subjects load failed." }
        }
    }
    val finish: (Result<Unit>, String) -> Unit = { result, message ->
        busy = false
        result.onSuccess {
            editor = null; deactivate = null; actionError = null
            notice = message; refresh++; onConfigSaved()
        }.onFailure { actionError = it.message ?: "Save failed. Your selection is kept; retry." }
    }
    BackHandler(enabled = editor != null || dirty) {
        if (!busy) { if (editor != null) { editor = null; actionError = null } else discard = true }
    }
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        if (editor != null) {
            SubjectEditor(editor!!, current?.subjects?.any { it.id == editor!!.id } == true, busy, actionError,
                onCancel = { editor = null; actionError = null }, onSave = { draft ->
                    if (!draft.isActive && current?.subjects?.any { it.id == draft.id && it.isActive } == true) deactivate = draft
                    else { busy = true; repository.save(schoolId, draft) { finish(it, "Subject saved.") } }
                })
        } else {
            val editable = current != null && current.classes.firstOrNull { it.id == classId }?.active == true &&
                current.sessions.firstOrNull { it.id == sessionId }?.status in setOf("Active", "Upcoming")
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Subject Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Current session: ${current?.sessions?.firstOrNull { it.current }?.name ?: "Not configured"}", color = SubjectGold, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { refresh++; actionError = null }, enabled = !loading && !busy && !dirty) { Icon(Icons.Outlined.Refresh, "Refresh subjects") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(tab == "directory", { tab = "directory"; query = "" }, label = { Text("Subjects Directory") }, enabled = !busy && !dirty)
                        FilterChip(tab == "assignments", { tab = "assignments"; query = "" }, label = { Text("Class Assignments") }, enabled = !busy && !dirty)
                    }
                    notice?.let { Text(it, color = Color(0xFF15803D)) }
                    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (current != null && error == null) {
                    item {
                        if (tab == "directory") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${current.subjects.count { it.isActive }} Active • ${current.subjects.size} Total", Modifier.weight(1f), color = SubjectMuted)
                                Button(onClick = { editor = SchoolSubject(displayOrder = (current.subjects.maxOfOrNull { it.displayOrder } ?: 0) + 1) }, enabled = !busy && !loading) { Text("Add Subject") }
                            }
                        } else {
                            SubjectSelect("Session", sessionId, current.sessions.map { it.id to (it.name + if (it.current) " (Current)" else "") }, !busy && !dirty && !loading) { sessionId = it; actionError = null }
                            SubjectSelect("Class", classId, current.classes.map { it.id to (it.name + if (!it.active) " (Inactive)" else "") }, !busy && !dirty && !loading) { classId = it; actionError = null }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${selected.size} subjects selected", Modifier.weight(1f), color = SubjectMuted)
                                TextButton(onClick = { selected = selected + current.subjects.filter { it.isActive }.map { it.id }; actionError = null }, enabled = editable && !busy && !loading) { Text("Select All Active") }
                            }
                            if (!editable) Text("Choose an active class and an Active/Upcoming session. Closed sessions are read-only.", color = SubjectMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search subjects or codes") }, singleLine = true)
                    }
                    val visible = current.subjects.filter { it.name.contains(query.trim(), true) || it.code.contains(query.trim(), true) }
                    if (visible.isEmpty()) item { Text("No matching subjects.", color = SubjectMuted) }
                    items(visible, key = { it.id }) { subject ->
                        val checked = subject.id in selected
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (tab == "assignments" && checked) SubjectGold else Color(0xFFE2E8F0)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${subject.code} • ${if (subject.isActive) "Active" else "Inactive"}", color = SubjectMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                if (tab == "directory") IconButton(onClick = { editor = subject; actionError = null }, enabled = !busy) { Icon(Icons.Outlined.Edit, "Edit ${subject.name}") }
                                else Checkbox(checked, onCheckedChange = { enabled -> selected = if (enabled) selected + subject.id else selected - subject.id; actionError = null }, enabled = editable && !busy && !loading && (subject.isActive || checked))
                            }
                        }
                    }
                }
            }
            if (tab == "assignments" && current != null) {
                Surface(tonalElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { selected = assigned; actionError = null }, enabled = dirty && !busy, modifier = Modifier.weight(1f)) { Text("Discard") }
                        Button(onClick = {
                            busy = true; actionError = null
                            repository.saveAssignments(schoolId, classId, sessionId, assigned, selected) { result ->
                                if (result.isSuccess) {
                                    var next = current.assignments
                                    (assigned + selected).forEach { id -> next = SubjectPolicy.assign(next, classId, sessionId, id, id in selected, java.util.UUID.randomUUID().toString()) }
                                    data = current.copy(assignments = next)
                                }
                                finish(result, "Assignments saved; teacher access updated.")
                            }
                        }, enabled = editable && dirty && !busy && !loading, modifier = Modifier.weight(1f)) { Text(if (busy) "Saving…" else "Save Assignments") }
                    }
                }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard unsaved selections?") }, confirmButton = { TextButton(onClick = { selected = assigned; discard = false }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    deactivate?.let { subject ->
        AlertDialog(onDismissRequest = { if (!busy) deactivate = null }, title = { Text("Deactivate subject?") }, text = { Column { Text("Existing records will be preserved."); actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton(onClick = { busy = true; repository.save(schoolId, subject) { finish(it, "Subject saved.") } }, enabled = !busy) { Text(if (busy) "Saving…" else "Deactivate") } }, dismissButton = { TextButton(onClick = { deactivate = null }, enabled = !busy) { Text("Cancel") } })
    }
}

@Composable
private fun SubjectEditor(entry: SchoolSubject, existing: Boolean, busy: Boolean, error: String?, onCancel: () -> Unit, onSave: (SchoolSubject) -> Unit) {
    var draft by remember(entry.id) { mutableStateOf(entry) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, enabled = !busy) { Icon(Icons.Outlined.ArrowBack, "Back") }
            Text(if (existing) "Edit Subject" else "Add Subject", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Subject Name") }, singleLine = true)
        OutlinedTextField(draft.code, { draft = draft.copy(code = it.uppercase(Locale.US).take(8)) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Subject Code") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Active", Modifier.weight(1f)); Switch(draft.isActive, { draft = draft.copy(isActive = it) }, enabled = !busy)
        }
        if (existing) Text("Renaming keeps the subject linked to classes, teachers and historical records.", color = SubjectMuted, style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(draft) }, enabled = !busy && draft.name.isNotBlank() && draft.code.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save Subject") }
        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun SubjectSelect(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 6.dp)) {
        Text(label, color = SubjectMuted, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Text(options.firstOrNull { it.first == value }?.second ?: "Select $label", Modifier.weight(1f)); Text("▾")
            }
            DropdownMenu(expanded && enabled, { expanded = false }) {
                options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) }) }
            }
        }
    }
}


