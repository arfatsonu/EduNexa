package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pk.edunexa.app.teachers.*

private val TeacherGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val TeacherMuted = Color(0xFF64748B)

@Composable
fun AdminTeachersScreen(schoolId: String, activeSessionId: String, modifier: Modifier = Modifier) {
    val repository = remember { TeacherDirectoryRepository() }
    var generatedLogin by remember { mutableStateOf<pk.edunexa.app.auth.GeneratedLogin?>(null) }
    var loginComplete by remember { mutableStateOf(false) }
    var newTeacherId by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<TeacherDirectoryData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(notice) { notice = null }
    var refresh by remember { mutableStateOf(0) }
    var tab by rememberSaveable { mutableStateOf("Teachers") }
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("All") }
    var selectedSession by rememberSaveable(activeSessionId) { mutableStateOf(activeSessionId) }
    var editor by remember { mutableStateOf<TeacherItem?>(null) }
    var assignmentEditor by remember { mutableStateOf<TeacherAssignment?>(null) }
    var loginTeacher by remember { mutableStateOf<TeacherItem?>(null) }
    var deletingTeacher by remember { mutableStateOf<TeacherItem?>(null) }
    var deleting by remember { mutableStateOf<TeacherAssignment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(schoolId, refresh) {
        loading = true
        repository.load(schoolId) { result ->
            loading = false
            result.onSuccess { data = it; error = null }.onFailure { error = it.message ?: "Could not load teachers." }
        }
    }
    val finish: (Result<Unit>, String) -> Unit = { result, success ->
        busy = false
        result.onSuccess {
            editor = null; assignmentEditor = null; loginTeacher = null; deleting = null; deletingTeacher = null
            formError = null; notice = success; refresh += 1
        }.onFailure { formError = it.message ?: "Firebase operation failed." }
    }
    BackHandler(enabled = editor != null || assignmentEditor != null) {
        if (!busy) { editor = null; assignmentEditor = null; formError = null }
    }
    if (loginComplete && generatedLogin != null) {
        GeneratedLoginDialog(generatedLogin!!) { generatedLogin = null; newTeacherId = null; loginComplete = false }
    }
    val current = data
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        if (editor != null && current != null) {
            TeacherEditor(
                schoolId = schoolId, item = editor!!, data = current, activeSessionId = activeSessionId,
                isNew = current.teachers.none { it.id == editor!!.id }, credentialLocked = newTeacherId == editor!!.id, busy = busy, error = formError,
                onCancel = { editor = null; formError = null },
                onSave = teacherSave@ { item ->
                    busy = true; formError = null
                    val isNew = current.teachers.none { it.id == item.id }
                    if (isNew || newTeacherId == item.id) {
                        if (newTeacherId != item.id) {
                            val candidate = runCatching {
                                val login = pk.edunexa.app.auth.LoginGenerator.generate("teacher", schoolId, item.id, item.employeeId, item.name, current.schoolShortName, item.portalEmail)
                                login.also { require(it.email !in current.reservedLoginEmails) { "This login email is already used. Choose another email." } }
                            }
                            if(candidate.isFailure) { busy = false; formError = candidate.exceptionOrNull()?.message; return@teacherSave }
                            newTeacherId = item.id
                            generatedLogin = candidate.getOrThrow()
                        }
                        repository.saveTeacher(schoolId, item) { saved ->
                            saved.onSuccess {
                                pk.edunexa.app.auth.ProfileLoginRepository().create(schoolId, "teacher", item.id, generatedLogin!!) { linked ->
                                    linked.onSuccess { loginComplete = true; finish(Result.success(Unit), "Teacher profile and login created.") }
                                        .onFailure { busy = false; formError = it.message ?: "Login creation failed. Retry saving." }
                                }
                            }.onFailure { finish(Result.failure(it), "") }
                        }
                    } else repository.saveTeacher(schoolId, item) { finish(it, "Teacher profile saved.") }
                }
            )
        } else if (assignmentEditor != null && current != null) {
            AssignmentEditor(assignmentEditor!!, current, busy, formError,
                onCancel = { assignmentEditor = null; formError = null },
                onSave = { assignment ->
                    busy = true; formError = null
                    repository.saveAssignment(schoolId, assignment) { finish(it, "Assignment saved; teacher permissions updated.") }
                })
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Teachers & Assignments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Live faculty directory", style = MaterialTheme.typography.bodySmall, color = TeacherMuted)
                }
                IconButton(onClick = { refresh += 1 }, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Refresh teachers") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Teachers", "Assignments").forEach { label ->
                    FilterChip(selected = tab == label, onClick = { tab = label; notice = null }, label = { Text(label) })
                }
            }
            notice?.let { Text(it, color = Color(0xFF15803D), modifier = Modifier.padding(vertical = 6.dp)) }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = { refresh += 1 }) { Text("Retry") }
            }
            if (loading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (current != null && error == null) {
                if (tab == "Teachers") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${current.teachers.count { it.status == "Active" }} Active • ${current.teachers.size} Total",
                            Modifier.weight(1f), color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { editor = TeacherItem(); formError = null }) { Icon(Icons.Outlined.Add, null); Text("Add Teacher") }
                    }
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search name / Employee ID") })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Active", "Inactive", "Left / Resigned").forEach { value ->
                            FilterChip(status == value, { status = value }, label = { Text(value) })
                        }
                    }
                    val visible = current.teachers.filter {
                        (status == "All" || it.status == status) &&
                            (it.name.contains(query, true) || it.employeeId.contains(query, true))
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (visible.isEmpty()) item { Text("No matching teachers.", color = TeacherMuted, modifier = Modifier.padding(20.dp)) }
                        items(visible, key = { it.id }) { item ->
                            FacultyCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    TeacherPhotoAvatar(item.profilePhoto, item.name)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("${item.employeeId} • ${item.status}", color = TeacherGold, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { editor = item; formError = null }, enabled = !busy) { Icon(Icons.Outlined.Edit, "Edit ${item.name}") }
                                    IconButton(onClick = { deletingTeacher = item; formError = null }, enabled = !busy) {
                                        Icon(Icons.Outlined.Delete, "Delete ${item.name}", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (item.qualification.isNotBlank()) Text(item.qualification, color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                                if (item.phone.isNotBlank()) Text(item.phone, style = MaterialTheme.typography.bodyMedium)
                                val own = current.assignments.filter { it.teacherId == item.id && it.academicSession == selectedSession }
                                if (item.inchargeClassId.isNotBlank()) Text(
                                    "Incharge: ${current.classLabel(item.inchargeClassId)} • ${current.sectionLabel(item.inchargeSectionId)} (${current.sessionLabel(item.inchargeAcademicSession.ifBlank { activeSessionId })})",
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                                Text("${own.size} ${if (own.size == 1) "subject assignment" else "subject assignments"} • ${current.sessionLabel(selectedSession)}", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { loginTeacher = item; formError = null }) { Text("Login Details") }
                                    TextButton(onClick = {
                                        tab = "Assignments"
                                        assignmentEditor = TeacherAssignment(teacherId = item.id, classId = "", sectionId = "", subjectId = "", academicSession = selectedSession)
                                        formError = null
                                    }, enabled = item.status == "Active") { Text("Assign Subject") }
                                }
                            }
                        }
                    }
                } else {
                    FacultySelect("Academic Session", selectedSession, current.sessions, true) { selectedSession = it }
                    Button(onClick = {
                        assignmentEditor = TeacherAssignment(teacherId = "", classId = "", sectionId = "", subjectId = "", academicSession = selectedSession)
                        formError = null
                    }, enabled = current.teachers.any { it.status == "Active" }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Icon(Icons.Outlined.Add, null); Text("Add Assignment")
                    }
                    val visible = current.assignments.filter { it.academicSession == selectedSession }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (visible.isEmpty()) item { Text("No teaching assignments for this session.", color = TeacherMuted, modifier = Modifier.padding(20.dp)) }
                        items(visible, key = { it.id }) { assignment ->
                            FacultyCard {
                                val owner = current.teachers.firstOrNull { it.id == assignment.teacherId }
                                Text(owner?.name ?: "Teacher record unavailable", fontWeight = FontWeight.Bold)
                                Text("${current.classLabel(assignment.classId)} • ${current.sectionLabel(assignment.sectionId)}", color = TeacherMuted)
                                Text(current.subjectLabel(assignment.subjectId), color = TeacherGold, fontWeight = FontWeight.SemiBold)
                                Row {
                                    TextButton(onClick = { assignmentEditor = assignment; formError = null }) { Text("Edit") }
                                    TextButton(onClick = { deleting = assignment; formError = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    loginTeacher?.let { teacher ->
        TeacherLoginDialog(schoolId, teacher, data?.schoolShortName.orEmpty(), busy, formError,
            onDismiss = { if (!busy) { loginTeacher = null; formError = null } },
            onCreate = { email, password ->
                busy = true; formError = null
                repository.createLogin(schoolId, teacher, email, password) { result ->
                    result.onSuccess {
                        generatedLogin = pk.edunexa.app.auth.GeneratedLogin(email.trim().lowercase(), password)
                        loginComplete = true
                        finish(Result.success(Unit), "Teacher Firebase login created.")
                    }.onFailure { finish(Result.failure(it), "") }
                }
            })
    }
    deletingTeacher?.let { teacher ->
        AlertDialog(
            onDismissRequest = { if (!busy) { deletingTeacher = null; formError = null } },
            title = { Text("Delete Teacher?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${teacher.name} • ${teacher.employeeId}", fontWeight = FontWeight.Bold)
                Text("This removes the teacher from the directory, disables portal access and removes all subject assignments and class-incharge responsibility. Past attendance, diaries and results stay saved.")
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = {
                busy = true; formError = null
                repository.deleteTeacher(schoolId, teacher.id) { finish(it, "Teacher deleted; assignments and portal access removed.") }
            }, enabled = !busy) { Text(if (busy) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingTeacher = null; formError = null }, enabled = !busy) { Text("Cancel") } }
        )
    }
    deleting?.let { assignment ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("Remove assignment?") },
            text = { Column { Text("Teacher access to this class/section/subject will be removed. Historical marks and diaries stay saved."); formError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(onClick = {
                busy = true; formError = null
                repository.removeAssignment(schoolId, assignment.id) { finish(it, "Assignment removed; access updated.") }
            }, enabled = !busy) { Text(if (busy) "Removing…" else "Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null; formError = null }, enabled = !busy) { Text("Cancel") } })
    }
}

@Composable
private fun FacultyCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun TeacherEditor(schoolId: String, item: TeacherItem, data: TeacherDirectoryData, activeSessionId: String, isNew: Boolean, credentialLocked: Boolean,
    busy: Boolean, error: String?, onCancel: () -> Unit, onSave: (TeacherItem) -> Unit) {
    var draft by remember(item.id) { mutableStateOf(item) }
    var photoProcessing by remember { mutableStateOf(false) }
    var allocatingId by remember(item.id) { mutableStateOf(isNew) }
    var idError by remember(item.id) { mutableStateOf<String?>(null) }
    var idRetry by remember(item.id) { mutableIntStateOf(0) }
    val registration = remember { pk.edunexa.app.students.RegistrationRepository() }
    LaunchedEffect(schoolId, item.id, isNew, idRetry) {
        if (isNew) {
            allocatingId = true
            idError = null
            registration.reserveTeacher(schoolId, item.id) { result ->
                allocatingId = false
                result.onSuccess { draft = draft.copy(employeeId = it) }
                    .onFailure { idError = it.message ?: "Could not allocate employee ID." }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorHeading(if (isNew) "Add Teacher" else "Edit Teacher", busy, onCancel)
        TeacherPhotoEditor(draft.profilePhoto, draft.name, !busy,
            onChanged = { draft = draft.copy(profilePhoto = it) },
            onProcessing = { photoProcessing = it })
        FacultyField("Full Name", draft.name, !busy) { draft = draft.copy(name = it) }
        OutlinedTextField(draft.employeeId, { draft = draft.copy(employeeId = it.trim()) }, Modifier.fillMaxWidth(),
            enabled = !busy && !allocatingId && !credentialLocked && draft.authUid.isBlank(),
            label = { Text("Employee ID") }, supportingText = { Text(if (draft.authUid.isBlank()) "Suggested automatically; you can edit before creating the login." else "Linked login: employee identity is protected.") }, singleLine = true)
        if (draft.authUid.isBlank()) {
            val suggested = runCatching { pk.edunexa.app.auth.StudentLoginIdentity.email(draft.name, draft.employeeId, data.schoolShortName, "teacher") }.getOrDefault("")
            OutlinedTextField(draft.portalEmail.ifBlank { suggested }, { draft = draft.copy(portalEmail = it.trim().lowercase()) }, Modifier.fillMaxWidth(), enabled = !busy && !credentialLocked,
                label = { Text("Login email") }, supportingText = { Text("Suggested from name + employee number; edit if needed.") }, singleLine = true)
        }
        if (allocatingId) Text("Allocating employee ID…", color = TeacherMuted)
        idError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { idRetry++ }, enabled = !busy && !allocatingId) { Text("Retry Employee ID") }
        }
        FacultyField("Phone", draft.phone, !busy) { draft = draft.copy(phone = it) }
        FacultyField("Contact Email", draft.email, !busy) { draft = draft.copy(email = it) }
        FacultyField("Qualification", draft.qualification, !busy) { draft = draft.copy(qualification = it) }
        FacultyField("Joining Date (YYYY-MM-DD)", draft.joiningDate, !busy) { draft = draft.copy(joiningDate = it) }
        FacultySelect("Status", draft.status, listOf("Active", "Inactive", "Left / Resigned").map { AcademicOption(it, it) }, !busy) { draft = draft.copy(status = it) }
        Text("Class Incharge", fontWeight = FontWeight.Bold)
        FacultySelect("Class", draft.inchargeClassId, listOf(AcademicOption("", "Not assigned")) + data.classes, !busy) {
            draft = draft.copy(inchargeClassId = it, inchargeSectionId = "", inchargeAcademicSession = if (it.isBlank()) "" else activeSessionId)
        }
        if (draft.inchargeClassId.isNotBlank()) {
            FacultySelect("Section", draft.inchargeSectionId, data.sections.filter { it.parentId == draft.inchargeClassId }, !busy) { draft = draft.copy(inchargeSectionId = it) }
            FacultySelect("Incharge Session", draft.inchargeAcademicSession.ifBlank { activeSessionId }, data.sessions.filter { it.id == activeSessionId }, !busy) { draft = draft.copy(inchargeAcademicSession = it) }
        }
        Text("Login details are managed separately after the profile is saved.", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(draft.copy(inchargeAcademicSession = if (draft.inchargeClassId.isBlank()) "" else draft.inchargeAcademicSession.ifBlank { activeSessionId })) },
            enabled = !busy && !photoProcessing && !allocatingId && idError == null && draft.name.isNotBlank() && draft.employeeId.isNotBlank() &&
                (draft.inchargeClassId.isBlank() || draft.inchargeSectionId.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Saving…" else "Save Teacher")
        }
        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AssignmentEditor(item: TeacherAssignment, data: TeacherDirectoryData, busy: Boolean, error: String?,
    onCancel: () -> Unit, onSave: (TeacherAssignment) -> Unit) {
    var draft by remember(item.id) { mutableStateOf(item) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditorHeading("Teacher Assignment", busy, onCancel)
        FacultySelect("Academic Session", draft.academicSession, data.sessions, !busy) { draft = draft.copy(academicSession = it, subjectId = "") }
        FacultySelect("Teacher", draft.teacherId, data.teachers.filter { it.status == "Active" }.map { AcademicOption(it.id, "${it.name} (${it.employeeId})") }, !busy) { draft = draft.copy(teacherId = it) }
        FacultySelect("Class", draft.classId, data.classes, !busy) { draft = draft.copy(classId = it, sectionId = "", subjectId = "") }
        FacultySelect("Section", draft.sectionId, data.sections.filter { it.parentId == draft.classId }, !busy && draft.classId.isNotBlank()) { draft = draft.copy(sectionId = it) }
        val subjects = data.subjectsFor(draft.classId, draft.academicSession)
        FacultySelect("Curriculum Subject", draft.subjectId, subjects, !busy && subjects.isNotEmpty()) { draft = draft.copy(subjectId = it) }
        if (draft.classId.isNotBlank() && subjects.isEmpty()) Text("No curriculum subjects assigned to this class/session yet.", color = TeacherMuted)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(draft) }, enabled = !busy && listOf(draft.teacherId, draft.classId, draft.sectionId, draft.subjectId, draft.academicSession).all { it.isNotBlank() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Saving…" else "Save Assignment")
        }
        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun EditorHeading(title: String, busy: Boolean, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel, enabled = !busy) { Icon(Icons.Outlined.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FacultyField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true, label = { Text(label) })
}

@Composable
private fun FacultySelect(label: String, value: String, options: List<AcademicOption>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = TeacherMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Text(options.firstOrNull { it.id == value }?.name ?: value.ifBlank { "Select $label" }, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded && enabled, { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option.name) }, onClick = { expanded = false; onSelect(option.id) })
                }
            }
        }
    }
}

@Composable
private fun TeacherLoginDialog(schoolId: String, teacher: TeacherItem, schoolShortName: String, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit) {
    val linked = teacher.authUid.isNotBlank()
    val suggestion = remember(teacher.id, schoolShortName) { runCatching {
        pk.edunexa.app.auth.LoginGenerator.generate("teacher", schoolId, teacher.id, teacher.employeeId, teacher.name, schoolShortName)
    } }
    var email by remember(teacher.id, schoolShortName) { mutableStateOf(teacher.portalEmail.ifBlank { suggestion.getOrNull()?.email.orEmpty() }) }
    var password by remember(teacher.id) { mutableStateOf(if(linked) "" else pk.edunexa.app.auth.LoginGenerator.password("teacher", schoolId, teacher.id, teacher.employeeId, teacher.name)) }
    var resetError by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Teacher Login") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(teacher.name, fontWeight = FontWeight.Bold)
                Text("Employee ID: ${teacher.employeeId}")
                if (linked) {
                    Text("Login Email: ${teacher.portalEmail.ifBlank { "Not recorded" }}")
                    Text("Existing login account. Password is securely held by Firebase Auth and cannot be displayed here.", color = TeacherMuted)
                    var resetSent by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Send password reset link:", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = {
                                val em = teacher.portalEmail.trim().lowercase()
                                if (em.contains("@")) {
                                    com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(em)
                                        .addOnSuccessListener { resetSent = true }
                                        .addOnFailureListener { resetError = it.message ?: "Could not send reset email." }
                                }
                            },
                            enabled = !busy && teacher.portalEmail.isNotBlank()
                        ) {
                            Text(if (resetSent) "Reset Link Sent ✔" else "Send Reset Email")
                        }
                    }
                } else {
                    if(suggestion.isFailure && email.isBlank()) Text(suggestion.exceptionOrNull()?.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                    FacultyField("Login Email", email, !busy) { email = it }
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
                        enabled = !busy, label = { Text("Initial Password (6+ characters)") }, visualTransformation = PasswordVisualTransformation())
                    Text("Email and a first-name + employee-ID password are generated automatically. You can copy the login after it is created.", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                }
                (resetError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (!linked) TextButton(onClick = { onCreate(email, password) }, enabled = !busy && email.isNotBlank() && password.length >= 6) {
                Text(if (busy) "Creating…" else "Create Login")
            } else TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = { if (!linked) TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } })
}


