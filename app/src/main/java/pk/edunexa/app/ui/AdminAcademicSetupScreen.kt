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
import androidx.compose.material.icons.outlined.Delete
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

private val AcademicGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val AcademicMuted = Color(0xFF64748B)

@Composable
fun AdminAcademicSetupScreen(schoolId: String, onConfigSaved: () -> Unit, modifier: Modifier = Modifier) {
    val repository = remember { AcademicSetupRepository() }
    var data by remember { mutableStateOf<AcademicSetupData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(notice) { notice = null }
    var refresh by remember { mutableStateOf(0) }
    var tab by rememberSaveable { mutableStateOf("classes") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedClass by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<AcademicEntry?>(null) }
    var editorType by remember { mutableStateOf("classes") }
    var busy by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<AcademicEntry?>(null) }
    var currentCandidate by remember { mutableStateOf<AcademicEntry?>(null) }

    LaunchedEffect(schoolId, refresh) {
        loading = true
        repository.load(schoolId) { result ->
            loading = false
            result.onSuccess { data = it; error = null }.onFailure { error = it.message ?: "Could not load academic settings." }
        }
    }
    val finish: (Result<Unit>, String) -> Unit = { result, message ->
        busy = false
        result.onSuccess {
            editor = null; currentCandidate = null; deleteCandidate = null; formError = null
            notice = message; refresh += 1; onConfigSaved()
        }.onFailure { formError = it.message ?: "Firebase update failed." }
    }
    BackHandler(enabled = editor != null) { if (!busy) { editor = null; formError = null } }
    val current = data
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        if (editor != null && current != null) {
            AcademicEditor(editor!!, editorType, current, busy, formError,
                onCancel = { editor = null; formError = null },
                onSave = { entry ->
                    busy = true; formError = null
                    repository.save(schoolId, editorType, entry) { finish(it, "Academic setup saved.") }
                })
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Classes & Sections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Current session: ${current?.currentSession?.name ?: "Not configured"}", color = AcademicGold, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { refresh += 1 }, enabled = !loading && !busy) { Icon(Icons.Outlined.Refresh, "Refresh academic setup") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("classes" to "Classes", "sections" to "Sections", "sessions" to "Sessions").forEach { (id, title) ->
                    FilterChip(tab == id, { tab = id; query = ""; notice = null }, label = { Text(title) })
                }
            }
            notice?.let { Text(it, color = Color(0xFF15803D), modifier = Modifier.padding(vertical = 6.dp)) }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp))
                OutlinedButton(onClick = { refresh += 1 }) { Text("Retry") }
            }
            if (loading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (current != null && error == null) {
                if (tab == "sections") AcademicSelect("Class", selectedClass,
                    listOf("" to "All Classes") + current.classes.map { it.id to it.name }) { selectedClass = it }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val count = when (tab) { "classes" -> current.classes.size; "sections" -> current.sections.size; else -> current.sessions.size }
                    Text("$count ${when (tab) { "classes" -> "Classes"; "sections" -> "Sections"; else -> "Sessions" }}", Modifier.weight(1f), color = AcademicMuted)
                    Button(onClick = {
                        editorType = tab; formError = null
                        val entries = when (tab) { "classes" -> current.classes; "sections" -> current.sections; else -> current.sessions }
                        editor = AcademicEntry(classId = if (tab == "sections") selectedClass else "",
                            displayOrder = (entries.maxOfOrNull { it.displayOrder } ?: 0) + 1)
                    }, enabled = tab != "sections" || current.classes.any { it.isActive }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text("Add ${when (tab) { "classes" -> "Class"; "sections" -> "Section"; else -> "Session" }}")
                    }
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") })
                Spacer(Modifier.height(10.dp))
                val visible = when (tab) {
                    "classes" -> current.classes
                    "sections" -> current.sections.filter { selectedClass.isBlank() || it.classId == selectedClass }
                    else -> current.sessions
                }.filter { it.name.contains(query, true) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (visible.isEmpty()) item { Text("No matching records.", color = AcademicMuted, modifier = Modifier.padding(20.dp)) }
                    items(visible, key = { it.id }) { entry ->
                        AcademicCard {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(if (tab == "sessions") (if (entry.isCurrent) "CURRENT • " else "") + entry.status else if (entry.isActive) "Active" else "Inactive",
                                        color = if (entry.isCurrent || entry.isActive) AcademicGold else AcademicMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                if (tab == "sections" && !entry.isActive) IconButton(onClick = { deleteCandidate = entry; formError = null }, enabled = !busy) {
                                    Icon(Icons.Outlined.Delete, "Delete ${entry.name}", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = { editor = entry; editorType = tab; formError = null }) { Icon(Icons.Outlined.Edit, "Edit ${entry.name}") }
                            }
                            when (tab) {
                                "classes" -> {
                                    if (entry.wing.isNotBlank()) Text(entry.wing, color = AcademicMuted, style = MaterialTheme.typography.bodySmall)
                                    val sections = current.sections.filter { it.classId == entry.id }
                                    Text("${sections.count { it.isActive }} active sections • ${sections.size} total", color = AcademicMuted, style = MaterialTheme.typography.bodySmall)
                                    Row {
                                        TextButton(onClick = { selectedClass = entry.id; tab = "sections"; query = "" }) { Text("View Sections") }
                                        TextButton(onClick = {
                                            editorType = "sections"; formError = null
                                            editor = AcademicEntry(classId = entry.id, displayOrder = (sections.maxOfOrNull { it.displayOrder } ?: 0) + 1)
                                        }, enabled = entry.isActive) { Text("Add Section") }
                                    }
                                }
                                "sections" -> Text(current.className(entry.classId), color = AcademicMuted, style = MaterialTheme.typography.bodySmall)
                                else -> {
                                    if (entry.startDate.isNotBlank() || entry.endDate.isNotBlank()) Text("${entry.startDate.ifBlank { "—" }} → ${entry.endDate.ifBlank { "—" }}", color = AcademicMuted, style = MaterialTheme.typography.bodySmall)
                                    if (!entry.isCurrent) TextButton(onClick = { currentCandidate = entry; formError = null }, enabled = entry.status != "Closed" && !busy) { Text("Set Current Session") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleteCandidate?.let { section ->
        AlertDialog(onDismissRequest = { if (!busy) { deleteCandidate = null; formError = null } },
            title = { Text("Delete inactive section?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Delete ${section.name} from ${data?.className(section.classId).orEmpty()}? It will be removed from the section list. Historical student and attendance records will be preserved.")
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = {
                busy = true; formError = null
                repository.deleteSection(schoolId, section.id) { finish(it, "Inactive section deleted.") }
            }, enabled = !busy) { Text(if (busy) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null; formError = null }, enabled = !busy) { Text("Cancel") } })
    }
    currentCandidate?.let { session ->
        AlertDialog(onDismissRequest = { if (!busy) currentCandidate = null }, title = { Text("Set current session?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${session.name} will become the current academic session. Dashboard and teacher assignments will use this session.")
                Text("Existing students, attendance, fees and results stay in their original sessions. This does not promote or transfer students.", color = AcademicMuted)
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = {
                busy = true; formError = null
                repository.setCurrent(schoolId, session.id) { finish(it, "Current session updated.") }
            }, enabled = !busy) { Text(if (busy) "Saving…" else "Set Current") } },
            dismissButton = { TextButton(onClick = { currentCandidate = null; formError = null }, enabled = !busy) { Text("Cancel") } })
    }
}

@Composable
private fun AcademicCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun AcademicEditor(entry: AcademicEntry, type: String, data: AcademicSetupData, busy: Boolean, error: String?, onCancel: () -> Unit, onSave: (AcademicEntry) -> Unit) {
    var draft by remember(entry.id) { mutableStateOf(entry) }
    var order by remember(entry.id) { mutableStateOf(entry.displayOrder.toString()) }
    val existing = when (type) { "classes" -> data.classes; "sections" -> data.sections; else -> data.sessions }.any { it.id == entry.id }
    val label = when (type) { "classes" -> "Class"; "sections" -> "Section"; else -> "Academic Session" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, enabled = !busy) { Icon(Icons.Outlined.ArrowBack, "Back") }
            Text("${if (existing) "Edit" else "Add"} $label", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        AcademicField("$label Name", draft.name, !busy) { draft = draft.copy(name = it) }
        when (type) {
            "classes" -> AcademicField("Wing", draft.wing, !busy) { draft = draft.copy(wing = it) }
            "sections" -> AcademicSelect("Parent Class", draft.classId,
                data.classes.filter { it.isActive || it.id == draft.classId }.map { it.id to it.name }, !busy && !existing) { draft = draft.copy(classId = it) }
            else -> {
                AcademicSelect("Status", draft.status, listOf("Active", "Upcoming", "Inactive", "Closed").map { it to it }, !busy && !draft.isCurrent) { draft = draft.copy(status = it) }
                AcademicField("Start Date (YYYY-MM-DD)", draft.startDate, !busy) { draft = draft.copy(startDate = it) }
                AcademicField("End Date (YYYY-MM-DD)", draft.endDate, !busy) { draft = draft.copy(endDate = it) }
                if (draft.isCurrent) Text("Current session must remain Active. Set another session current before closing this one.", color = AcademicMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (type != "sessions") {
            AcademicField("Display Order", order, !busy) { order = it }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Active", Modifier.weight(1f))
                Switch(draft.isActive, { draft = draft.copy(isActive = it) }, enabled = !busy)
            }
            if (existing) Text("Renaming keeps all linked students and assignments attached.", color = AcademicMuted, style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(draft.copy(displayOrder = order.toIntOrNull() ?: 0)) },
            enabled = !busy && draft.name.isNotBlank() && (type != "sections" || draft.classId.isNotBlank()) &&
                (type == "sessions" || order.toIntOrNull()?.let { it in 0..9999 } == true), modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Saving…" else "Save $label")
        }
        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AcademicField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true, label = { Text(label) })
}

@Composable
private fun AcademicSelect(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = AcademicMuted, style = MaterialTheme.typography.labelMedium)
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


