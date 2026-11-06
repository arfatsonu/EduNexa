package pk.edunexa.app.ui

import pk.edunexa.app.academic.AcademicSessionPolicy

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.diary.*
import pk.edunexa.app.exams.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ExamGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val ExamMuted = Color(0xFF64748B)
private fun examToday() = pk.edunexa.app.fees.FeePolicy.today()
private fun Map<String, Any?>.examText(key: String) = this[key]?.toString().orEmpty()

@Composable
fun ExamScreen(session: PortalSession, initialTab: String = "Tests", modifier: Modifier = Modifier) {
    val repo = remember(session.uid) { ExamRepository(session) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    var data by remember { mutableStateOf<ExamData?>(null) }
    var loading by remember { mutableStateOf(true) }; var refresh by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var notice by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(notice) { notice = null }
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var sessionId by rememberSaveable { mutableStateOf("") }; var classId by rememberSaveable { mutableStateOf("") }; var groupId by rememberSaveable { mutableStateOf("") }; var query by rememberSaveable { mutableStateOf("") }
    var resultReason by remember { mutableStateOf("") }
    var testType by rememberSaveable { mutableStateOf("") }; var boardStage by rememberSaveable { mutableStateOf("") }; var statusFilter by rememberSaveable { mutableStateOf("Active") }; var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var editingSheet by remember { mutableStateOf<DiaryItem?>(null) }; var viewingSheet by remember { mutableStateOf<DiaryItem?>(null) }; var details by remember { mutableStateOf<DiaryItem?>(null) }
    var editing by remember { mutableStateOf<DiaryItem?>(null) }; var marking by remember { mutableStateOf<DiaryItem?>(null) }; var action by remember { mutableStateOf<Pair<DiaryItem, String>?>(null) }
    LaunchedEffect(session.uid, refresh) { loading = true; runCatching { repo.load() }.onSuccess { data = it; error = null; if (sessionId.isBlank()) sessionId = it.currentSession.examText("id") }.onFailure { error = it.message ?: "Exams load failed." }; loading = false }
    fun runOperation(message: String, operation: suspend () -> Unit) { busy = true; error = null; scope.launch { runCatching { operation() }.onSuccess { busy = false; editing = null; editingSheet = null; marking = null; action = null; notice = message; refresh += 1 }.onFailure { busy = false; error = it.message ?: "Operation failed." } } }
    BackHandler(enabled = editing != null || editingSheet != null || marking != null) { if (!busy) { editing = null; editingSheet = null; marking = null; error = null } }
    val current = data
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        when {
            editingSheet != null && current != null -> DateSheetEditor(editingSheet!!, current, busy, error, onBack = { editingSheet = null; error = null }) { draft -> runOperation("Term datesheet saved.") { val saved = repo.saveDateSheet(draft); viewingSheet = saved; tab = "Datesheets" } }
            editing != null && current != null -> TestEditor(editing!!, current, session, busy, error, onBack = { editing = null; error = null }) { draft -> runOperation("Test saved.") { repo.saveTest(draft) } }
            marking != null && current != null -> MarksEditor(marking!!, current, repo.admin || marking!!.text("teacherId") != session.profileId || !TeacherDiaryPolicy.canWrite(current.config,marking!!,session.profileId.orEmpty()), busy, error, onBack = { marking = null; error = null }) { rows -> runOperation("Marks saved.") { repo.saveMarks(marking!!, rows) } }
            else -> {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Exams & Results", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Current session: ${current?.currentSession?.examText("name").orEmpty()}", color = ExamGold, style = MaterialTheme.typography.bodySmall) }
                    IconButton(onClick = { refresh += 1 }, enabled = !busy && !loading) { Icon(Icons.Outlined.Refresh, "Refresh exams") }
                }
                PortalTabs(listOf("Tests", "Datesheets", "Results"), tab, !busy) { tab = it; query = ""; notice = null }
                notice?.let { Text(it, color = Color(0xFF15803D), modifier = Modifier.padding(vertical = 6.dp)) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refresh += 1 }, enabled = !busy) { Text("Retry / Refresh") } }
                if (loading) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else if (current != null) {
                    val configuredSession = current.options("sessions").firstOrNull { it["id"] == sessionId }
                    val sessionAliases = listOf(sessionId, configuredSession?.examText("name").orEmpty())
                    fun inSession(item: DiaryItem) = AcademicSessionPolicy.matches(item.text("academicSessionId").ifBlank { item.text("academicSession") }, sessionAliases)
                    if (tab != "Datesheets") {
                        ExamSelect("Test Type", testType, listOf("" to "All Test Types") + ExamPolicy.types.map { it to ExamPolicy.typeLabel(it) }, !busy) { testType = it; boardStage = ""; groupId = "" }
                        if (testType == "Board Test Session") {
                            val stages = (ExamPolicy.stages + current.tests.filter { it.text("assessmentType") == "Board Test Session" }.map { it.text("boardStage") }.filter { it.isNotBlank() }).distinct()
                            ExamSelect("Pre-board Stage", boardStage, listOf("" to "All Stages") + stages.map { it to it }, !busy) { boardStage = it }
                        }
                    }
                    TextButton(onClick = { filtersExpanded = !filtersExpanded }, enabled = !busy) {
                        val className = current.options("classes").firstOrNull { it["id"] == classId }?.examText("name") ?: "All Classes"
                        val sessionName = current.options("sessions").firstOrNull { it["id"] == sessionId }?.examText("name").orEmpty()
                        Text("${if (filtersExpanded) "Hide Filters" else "More Filters"} • $className • $sessionName")
                    }
                    if (filtersExpanded) AlertDialog(onDismissRequest = { filtersExpanded = false }, title = { Text("Exam Filters") },
                        confirmButton = { TextButton(onClick = { filtersExpanded = false }) { Text("Done") } }, text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        ExamSelect("Academic Session", sessionId, current.options("sessions").map { it.examText("id") to it.examText("name") }, !busy) { sessionId = it; groupId = "" }
                        ExamSelect("Class", classId, listOf("" to "All Classes") + current.options("classes").map { it.examText("id") to it.examText("name") }, !busy) { classId = it; groupId = "" }
                        if (tab != "Datesheets") {
                            ExamSelect("Saved Datesheet / Exam Session", groupId, listOf("" to "All / Individual Tests") + current.groups.filter { inSession(it) && (classId.isBlank() || it.text("targetClassId") in listOf("", "ALL", classId)) && (testType.isBlank() || it.text("assessmentType").isBlank() || it.text("assessmentType") == testType) }.map { it.id to it.text("name") }, !busy) { groupId = it }
                        }
                    } })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (tab == "Results") "Saved marks & publication" else if (tab == "Datesheets") "Class term datesheets" else "Scheduled tests", Modifier.weight(1f), color = ExamMuted, style = MaterialTheme.typography.bodySmall)
                        if (repo.admin && tab == "Datesheets") TextButton(onClick = { error = null; editingSheet = DiaryItem(raw = mapOf("academicSessionId" to sessionId, "targetClassId" to classId, "assessmentType" to "1st Term")) }, enabled = !busy && current.options("sessions").any { it["id"] == sessionId && it["status"] in setOf("Active", "Upcoming") }) { Text("Create Datesheet") }
                        if (!repo.admin && tab == "Tests") TextButton(onClick = {
                            error = null
                            editing = DiaryItem(raw = mapOf("academicSessionId" to sessionId, "date" to examToday(), "classId" to classId, "assessmentType" to testType.ifBlank { "Class Test" }, "boardStage" to boardStage, "examSessionId" to groupId))
                        }, enabled = !busy && current.options("sessions").any { it["id"] == sessionId && it["status"] in setOf("Active", "Upcoming") }) { Text("Add Test") }
                    }
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search title or subject") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(12.dp), singleLine = true)
                    val tests = current.tests.filter { inSession(it) && (classId.isBlank() || it.text("classId") == classId) && (groupId.isBlank() || it.text("examSessionId") == groupId) && (testType.isBlank() || it.text("assessmentType") == testType) && (boardStage.isBlank() || it.text("boardStage") == boardStage) && (it.text("title").contains(query.trim(), true) || it.text("subject").contains(query.trim(), true)) }
                    if (tab == "Tests" || tab == "Results") {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = statusFilter == "Active", onClick = { statusFilter = "Active" }, label = { Text("Active Tests") })
                            FilterChip(selected = statusFilter == "Completed", onClick = { statusFilter = "Completed" }, label = { Text("Completed Tests") })
                            FilterChip(selected = statusFilter == "All", onClick = { statusFilter = "All" }, label = { Text("All (${tests.size})") })
                        }
                    }
                    val displayTests = tests.filter { test ->
                        val rList = current.rows(test)
                        val sIds = (test.raw["studentIds"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: current.roster(test).map { it.id }.toSet()
                        val done = test.text("status") == "Completed" || (sIds.isNotEmpty() && rList.count { it.raw["marksObtained"] is Number || it.text("status") in listOf("Absent", "Exempt") } >= sIds.size)
                        when (statusFilter) {
                            "Active" -> !done
                            "Completed" -> done
                            else -> true
                        }
                    }
                    if (tab == "Results" && tests.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { ExamPrinter.print(context, current.schoolName, tests, current.marks, session.schoolId) }, enabled = !busy) { Text("Print / PDF Gazette") }
                            TextButton(onClick = { details = tests.first() }, enabled = !busy) { Text("View Gazette Table") }
                        }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                        if (tab == "Datesheets") {
                            val groups = current.groups.filter { inSession(it) && (classId.isBlank() || it.text("targetClassId") in listOf("", "ALL", classId)) && it.text("name").contains(query.trim(), true) }
                            if (groups.isEmpty()) item { PortalEmptyState(Icons.Outlined.School, "No datesheets yet", "Class term datesheets will appear here for the selected academic session.") }
                            items(groups, key = { it.id }) { group -> ExamCard {
                                Text(group.text("name"), fontWeight = FontWeight.Bold); Text("${group.text("startDate")} → ${group.text("endDate")}", color = ExamMuted)
                                Text(group.text("status"), color = ExamGold)
                                
                                if (group.text("kind") == "termDatesheet") {
                                    Text("Term: ${ExamPolicy.typeLabel(group.text("assessmentType"))}", color = ExamGold)
                                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                                        TextButton(onClick = { viewingSheet = group }) { Text("View Datesheet") }
                                        TextButton(onClick = { DateSheetPrinter.print(context, current.schoolName, group,session.schoolId) }) { Text("A4 Print / Save PDF") }
                                        if (repo.admin) TextButton(onClick = { editingSheet = group; error = null }) { Text("Edit") }
                                    }
                                }
                                TextButton(onClick = { groupId = group.id; testType = group.text("assessmentType"); boardStage = ""; classId = group.text("targetClassId").takeUnless { it == "ALL" }.orEmpty(); tab = "Tests" }) { Text("View Linked Tests") }
                            } }
                        } else {
                            if (tests.isEmpty()) item { PortalEmptyState(Icons.Outlined.School, "No tests in this selection", "Change the test type or filters to view scheduled tests and their results.") }
                            items(displayTests, key = { it.id }) { test ->
                                val rows = current.rows(test); val flow = current.workflow(test); val locked = flow["isLocked"] == true || flow["isPublished"] == true
                                val editable = !repo.admin && !locked && test.text("teacherId") == session.profileId && TeacherDiaryPolicy.canWrite(current.config,test,session.profileId.orEmpty())
                                val ids = (test.raw["studentIds"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: current.roster(test).map { it.id }.toSet()
                                val status = ExamPolicy.status(test, rows, ids, examToday())
                                ExamCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(test.text("title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("${test.text("className")} • ${test.text("section")} • ${test.text("subject")}", color = ExamGold, fontWeight = FontWeight.SemiBold)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (status == "Completed") Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                                        ) {
                                            Text(
                                                text = if (status == "Completed") "Completed ✔" else "Awaiting Marks",
                                                color = if (status == "Completed") Color(0xFF166534) else Color(0xFFB45309),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Date: ${test.text("date")}" + (if (test.text("startTime").isNotBlank()) " • ${test.text("startTime")}" else ""), color = ExamMuted, style = MaterialTheme.typography.bodySmall)
                                        Text("Total: ${test.raw["totalMarks"] ?: "—"}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Marked: ${rows.count { it.raw["marksObtained"] is Number || it.text("status") in listOf("Absent", "Exempt") }} / ${ids.size} • Teacher: ${test.text("teacherName")}", color = ExamMuted, style = MaterialTheme.typography.bodySmall)
                                    if (test.text("assessmentType") == "Board Test Session" && test.text("boardStage").isNotBlank()) {
                                        Text("Stage: ${test.text("boardStage")}", color = ExamGold, style = MaterialTheme.typography.bodySmall)
                                    }
                                    val pendingResult = repo.pendingResultAction(test.id)
                                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                                        TextButton(onClick = { details = test }) { Text("Details") }
                                        TextButton(onClick = { marking = test; error = null }, enabled = !busy) { Text(if (!editable) "Marks" else "Enter Marks") }
                                        if (!repo.admin && tab == "Tests") {
                                            TextButton(onClick = { editing = test; error = null }, enabled = !busy && !locked) { Text("Edit") }
                                            TextButton(onClick = { action = test to "Delete"; error = null }, enabled = !busy && !locked) { Text("Delete") }
                                        }
                                        if (pendingResult != null) {
                                            TextButton(onClick = { resultReason = repo.pendingResultReason(test.id); action = test to ("Retry " + pendingResult); error = null }, enabled = !busy) { Text("Retry request") }
                                        } else if (repo.admin) {
                                            val commands = if (flow["isPublished"] == true) listOf("Unlock") else if (locked) listOf("Publish", "Unlock") else listOf("Lock", "Publish", "Return")
                                            commands.forEach { command -> TextButton(onClick = { resultReason = ""; action = test to command; error = null }, enabled = !busy) { Text(command) } }
                                        } else if (editable) {
                                            TextButton(onClick = { resultReason = ""; action = test to "Submit"; error = null }, enabled = !busy) { Text("Submit") }
                                        }
                                        if (tab == "Results") TextButton(onClick = { ExamPrinter.print(context, current.schoolName, listOf(test), current.marks, session.schoolId) }) { Text("Print") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    details?.let { TestDetailsDialog(it, onClose = { details = null }) }
    viewingSheet?.let { sheet -> DateSheetDetailsDialog(sheet, onClose = { viewingSheet = null }, onPrint = { DateSheetPrinter.print(context, data?.schoolName.orEmpty(), sheet,session.schoolId) }) }
    action?.let { (test, command) -> AlertDialog(onDismissRequest = { if (!busy) action = null }, title = { Text("$command result/test?") },
        text = { Column { Text(test.text("title")); Text(when (command) { "Unlock" -> "This removes published snapshots and allows marks edits."; "Publish" -> "Complete marks will be locked and published to linked student profiles."; "Delete" -> "Only a test without saved marks or locked results can be deleted."; else -> "Confirm $command for this test." }); if (command != "Delete" && !command.startsWith("Retry ")) OutlinedTextField(value = resultReason, onValueChange = { resultReason = it }, label = { Text("Reason / review note") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(onClick = { runOperation("$command completed.") {
            if (command == "Delete") repo.deleteTest(test)
            else repo.changeResult(test, data?.workflow(test).orEmpty(), command.removePrefix("Retry ").lowercase(Locale.ROOT), resultReason)
        } }, enabled = !busy) { Text(if (busy) "Saving…" else command) } }, dismissButton = { TextButton(onClick = { action = null; error = null }, enabled = !busy) { Text("Cancel") } }) }
}

@Composable
private fun TestEditor(entry: DiaryItem, data: ExamData, actor: PortalSession, busy: Boolean, error: String?, onBack: () -> Unit, onSave: (DiaryItem) -> Unit) {
    var fields by remember(entry.id) { mutableStateOf(entry.raw) }; var total by remember { mutableStateOf(entry.raw["totalMarks"]?.toString().orEmpty()) }; var passing by remember { mutableStateOf(entry.raw["passingMarks"]?.toString().orEmpty()) }
    fun value(key: String) = fields.examText(key)
    fun change(key: String, text: String) { fields = fields + (key to text) }
    val admin = actor.role == PortalRole.ADMIN; val existing = entry.id.isNotBlank(); val cls = value("classId"); val section = value("sectionId"); val session = value("academicSessionId")
    val assigned = data.options("teacherAssignments").filter { admin || it["teacherId"] == actor.profileId }
    val subjects = data.options("subjects").filter { it["isActive"] == true && data.options("classSubjectAssignments").any { a -> a["classId"] == cls && a["subjectId"] == it["id"] && a["academicSession"] == session } && (admin || assigned.any { a -> a["classId"] == cls && a["sectionId"] == section && a["subjectId"] == it["id"] && a["academicSession"] == session }) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExamEditorHeader(if (existing) "Edit Test" else "Add Test", busy, onBack)
        ExamSelect("Session", session, data.options("sessions").filter { it["status"] in setOf("Active", "Upcoming") }.map { it.examText("id") to it.examText("name") }, !busy && !existing) { change("academicSessionId", it); change("subjectId", "") }
        if (existing) Text("${entry.text("className")} • ${entry.text("section")} • ${entry.text("subject")}", color = ExamGold)
        else {
            ExamSelect("Class", cls, data.options("classes").filter { it["isActive"] == true && (admin || assigned.any { a -> a["classId"] == it["id"] && a["academicSession"] == session }) }.map { it.examText("id") to it.examText("name") }, !busy) { change("classId", it); change("sectionId", ""); change("subjectId", "") }
            ExamSelect("Section", section, data.options("sections").filter { it["isActive"] == true && it["classId"] == cls && (admin || assigned.any { a -> a["sectionId"] == it["id"] && a["academicSession"] == session }) }.map { it.examText("id") to it.examText("name") }, !busy) { change("sectionId", it); change("subjectId", "") }
            ExamSelect("Subject", value("subjectId"), subjects.map { it.examText("id") to it.examText("name") }, !busy) { change("subjectId", it) }
        }
        ExamField("Title", value("title"), !busy) { change("title", it) }
        ExamSelect("Test Type", value("assessmentType"), ExamPolicy.types.map { it to ExamPolicy.typeLabel(it) }, !busy) { change("assessmentType", it); change("examSessionId", ""); if (it != "Board Test Session") change("boardStage", "") }
        if (value("assessmentType") == "Board Test Session") ExamSelect("Pre-board Stage", value("boardStage"), (ExamPolicy.stages + listOf(value("boardStage")).filter { it.isNotBlank() && it !in ExamPolicy.stages }).map { it to it }, !busy) { change("boardStage", it) }
        ExamSelect("Exam Session (optional)", value("examSessionId"), listOf("" to "Individual test") + data.groups.filter { it.text("academicSessionId") == session && (it.text("targetClassId") in listOf("", "ALL", cls)) && (it.text("targetSectionId") in listOf("", section)) && (it.text("assessmentType").isBlank() || it.text("assessmentType") == value("assessmentType")) }.map { it.id to it.text("name") }, !busy) { change("examSessionId", it) }
        ExamDate("Date", value("date"), !busy) { change("date", it) }
        ExamField("Start Time (HH:mm)", value("startTime"), !busy) { change("startTime", it) }
        ExamField("Duration", value("duration"), !busy) { change("duration", it) }
        ExamField("Total Marks", total, !busy, numeric = true) { total = it }; ExamField("Passing Marks", passing, !busy, numeric = true) { passing = it }
        ExamField("Syllabus / Chapters", value("syllabus"), !busy) { change("syllabus", it) }; ExamField("Instructions", value("instructions"), !busy) { change("instructions", it) }
        if (existing) ExamSelect("Status", value("status"), listOf("Scheduled", "Awaiting Marks", "Completed", "Cancelled").map { it to it }, !busy) { change("status", it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(DiaryItem(entry.id, fields + mapOf("totalMarks" to total.toDoubleOrNull(), "passingMarks" to passing.toDoubleOrNull()))) }, enabled = !busy && value("title").isNotBlank() && value("subjectId").isNotBlank() && total.toDoubleOrNull() != null && passing.toDoubleOrNull() != null, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save Test") }
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun MarksEditor(test: DiaryItem, data: ExamData, readOnly: Boolean, busy: Boolean, error: String?, onBack: () -> Unit, onSave: (List<Map<String, Any?>>) -> Unit) {
    val context = LocalContext.current
    val currentSchoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }
    val previous = data.rows(test)
    var rows by remember(test.id) { mutableStateOf(run {
        val roster = data.roster(test).map { student -> previous.firstOrNull { it.text("studentId") == student.id }?.raw ?: mapOf("studentId" to student.id, "studentName" to student.text("fullName"), "rollNo" to student.text("rollNo"), "admissionNo" to student.text("admissionNo"), "status" to "Present", "marksObtained" to null, "remarks" to "") }
        val combined = roster + previous.filter { old -> roster.none { it["studentId"] == old.text("studentId") } }.map { it.raw }
        combined.sortedWith(compareBy(
            { (it["rollNo"]?.toString()?.filter { ch -> ch.isDigit() }?.toIntOrNull()) ?: Int.MAX_VALUE },
            { it["rollNo"]?.toString().orEmpty() },
            { it["studentName"]?.toString().orEmpty() }
        ))
    }) }
    var inputs by remember(test.id) { mutableStateOf(rows.associate { it.examText("studentId") to ((it["marksObtained"] as? Number)?.toString().orEmpty()) }) }
    val locked = readOnly || data.workflow(test)["isLocked"] == true || data.workflow(test)["isPublished"] == true || test.text("status") == "Cancelled"
    fun change(id: String, key: String, value: Any?) { rows = rows.map { if (it["studentId"] == id) it + (key to value) else it } }
    Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        ExamEditorHeader(if (locked) "View Marks" else "Enter Marks", busy, onBack)
        Text(test.text("title"), fontWeight = FontWeight.Bold)
        Text("Test Type: ${ExamPolicy.typeLabel(test.text("assessmentType"))}", color = ExamGold)
        if (test.text("assessmentType") == "Board Test Session") Text("Pre-board Stage: ${test.text("boardStage").ifBlank { "Not specified" }}", color = ExamGold)
        Text("Total: ${test.raw["totalMarks"]} • Passing: ${test.raw["passingMarks"]}", color = ExamGold)
        if (locked) Text(if (readOnly) "View only: editing is restricted to the assigned teacher" else "Locked / cancelled: view only", color = ExamMuted)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            if (rows.isEmpty()) item { Text("No students enrolled for this test.", color = ExamMuted) }
            items(rows, key = { it.examText("studentId") }) { row -> val id = row.examText("studentId"); ExamCard {
                Text(row.examText("studentName"), fontWeight = FontWeight.Bold); Text("Roll ${row.examText("rollNo")} • ${row.examText("admissionNo")}", color = ExamMuted, style = MaterialTheme.typography.bodySmall)
                ExamSelect("Status", row.examText("status"), listOf("Present", "Absent", "Exempt").map { it to it }, !busy && !locked) { status -> change(id, "status", status); if (status != "Present") { change(id, "marksObtained", null); inputs = inputs + (id to "") } }
                ExamField("Marks Obtained", inputs[id].orEmpty(), !busy && !locked && row["status"] == "Present", true) { text -> inputs = inputs + (id to text); change(id, "marksObtained", text.toDoubleOrNull()) }
                ExamField("Remarks", row.examText("remarks"), !busy && !locked) { change(id, "remarks", it) }

                // 1-Click WhatsApp & SMS test marks share
                val studentPhone = data.students.firstOrNull { it.id == id }?.text("guardianPhone").orEmpty().ifBlank { data.students.firstOrNull { it.id == id }?.text("phone").orEmpty() }
                if (studentPhone.isNotBlank() && inputs[id]?.isNotBlank() == true) {
                    val sName = currentSchoolName
                    val obt = inputs[id].orEmpty()
                    val tot = test.raw["totalMarks"]?.toString() ?: "0"
                    val pct = runCatching { "%.1f".format((obt.toDouble() / tot.toDouble()) * 100) }.getOrDefault("0")
                    val passFail = if ((obt.toDoubleOrNull() ?: 0.0) >= ((test.raw["passingMarks"] as? Number)?.toDouble() ?: 0.0)) "Passed ✔" else "Failed"
                    val msg = pk.edunexa.app.notifications.DirectMessageHelper.testMarksText(
                        schoolName = sName,
                        studentName = row.examText("studentName"),
                        rollNo = row.examText("rollNo"),
                        subject = test.text("subject"),
                        testTitle = test.text("title"),
                        obtained = obt,
                        total = tot,
                        percentage = pct,
                        status = passFail
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Share with parent: ", fontSize = 11.sp, color = ExamMuted)
                        IconButton(
                            onClick = { pk.edunexa.app.notifications.DirectMessageHelper.sendWhatsApp(context, studentPhone, msg) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            WhatsAppIcon(Modifier.size(20.dp), contentDescription = "WhatsApp")
                        }
                        IconButton(
                            onClick = { pk.edunexa.app.notifications.DirectMessageHelper.sendSms(context, studentPhone, msg) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Message, contentDescription = "SMS", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } }
        }
        if (!locked) Button(onClick = { onSave(rows) }, enabled = !busy && rows.isNotEmpty() && inputs.values.all { it.isBlank() || it.toDoubleOrNull() != null }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save Marks") }
    }
}
@Composable private fun ExamEditorHeader(title: String, busy: Boolean, onBack: () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.Outlined.ArrowBack, "Back") }; Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
@Composable private fun ExamCard(content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) } }
@Composable private fun ExamField(label: String, value: String, enabled: Boolean, numeric: Boolean = false, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text)) }
@Composable private fun ExamSelect(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    PortalSelect(label, value, options, enabled, onSelect)
}
@Composable private fun ExamDate(label: String, value: String, enabled: Boolean, onSelect: (String) -> Unit) { val context = LocalContext.current; TextButton(onClick = { val cal = Calendar.getInstance(); runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)?.let { cal.time = it } }; DatePickerDialog(context, { _, y, m, d -> onSelect(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }, enabled = enabled) { Text("$label: ${value.ifBlank { "Select date" }}") } }

@Composable
private fun TestDetailsDialog(test: DiaryItem, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Test Details") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(test.text("title"), fontWeight = FontWeight.Bold)
            Text("Test Type: ${ExamPolicy.typeLabel(test.text("assessmentType"))}", color = ExamGold)
            if (test.text("assessmentType") == "Board Test Session") Text("Pre-board Stage: ${test.text("boardStage").ifBlank { "Not specified" }}", color = ExamGold)
            listOf("Session" to "academicSession", "Class" to "className", "Section" to "section", "Subject" to "subject", "Assigned Teacher" to "teacherName", "Date" to "date", "Start Time" to "startTime", "Duration" to "duration", "Total Marks" to "totalMarks", "Passing Marks" to "passingMarks", "Syllabus / Chapters" to "syllabus", "Instructions" to "instructions", "Status" to "status").forEach { (label, key) -> Text("$label: ${test.text(key).ifBlank { "Not specified" }}") }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
private fun DateSheetEditor(entry: DiaryItem, data: ExamData, busy: Boolean, error: String?, onBack: () -> Unit, onSave: (DiaryItem) -> Unit) {
    var sessionId by remember(entry.id) { mutableStateOf(entry.text("academicSessionId")) }
    var classId by remember(entry.id) { mutableStateOf(entry.text("targetClassId")) }
    var sectionId by remember(entry.id) { mutableStateOf(entry.text("targetSectionId")) }
    var term by remember(entry.id) { mutableStateOf(entry.text("assessmentType").ifBlank { "1st Term" }) }
    var instructions by remember(entry.id) { mutableStateOf(entry.text("description")) }
    var rows by remember(entry.id) { mutableStateOf(DiaryPolicy.maps(entry.raw["datesheetEntries"])) }
    var validationError by remember(entry.id) { mutableStateOf<String?>(null) }
    var commonStart by remember(entry.id) { mutableStateOf("") }
    var commonEnd by remember(entry.id) { mutableStateOf("") }
    val existing = entry.id.isNotBlank()
    val subjectIds = data.options("classSubjectAssignments").filter { it["classId"] == classId && it["academicSession"] == sessionId }.map { it["subjectId"].toString() }.toSet()
    val subjects = data.options("subjects").filter { it["id"] in subjectIds && it["isActive"] == true }
    LaunchedEffect(sessionId, classId, subjects.map { it["id"] }) {
        rows = subjects.map { subject ->
            val old = rows.firstOrNull { it["subjectId"] == subject["id"] }
            (old ?: mapOf("subjectId" to subject["id"], "date" to "", "startTime" to "", "endTime" to "", "included" to !existing)) +
                ("subject" to subject["name"])
        }
    }
    fun change(id: String, key: String, value: Any) {
        rows = rows.map { if (it["subjectId"] == id) it + (key to value) else it }
        validationError = null
    }
    val selected = DateSheetPolicy.selectedEntries(rows)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExamEditorHeader(if (existing) "Edit Term Datesheet" else "Create Term Datesheet", busy, onBack)
        ExamSelect("Academic Session", sessionId, data.options("sessions").filter { it["status"] in setOf("Active", "Upcoming") }.map { it.examText("id") to it.examText("name") }, !busy && !existing) { sessionId = it; classId = ""; sectionId = ""; rows = emptyList(); validationError = null }
        ExamSelect("Class", classId, data.options("classes").filter { it["isActive"] == true }.map { it.examText("id") to it.examText("name") }, !busy && !existing) { classId = it; sectionId = ""; rows = emptyList(); validationError = null }
        ExamSelect("Section", sectionId, data.options("sections").filter { it["classId"] == classId && it["isActive"] == true }.map { it.examText("id") to it.examText("name") }, !busy && !existing) { sectionId = it; validationError = null }
        ExamSelect("Term", term, DateSheetPolicy.terms.map { it to ExamPolicy.typeLabel(it) }, !busy && !existing) { term = it; validationError = null }
        Text("Select papers, then set a date and timing for each selected subject.", color = ExamMuted, style = MaterialTheme.typography.bodySmall)
        if (classId.isNotBlank() && subjects.isEmpty()) Text("No subjects assigned to this class/session. Assign curriculum subjects first.", color = MaterialTheme.colorScheme.error)
        if (rows.isNotEmpty()) ExamCard {
            Text("Common paper timing", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { DateSheetTime("Start", commonStart, !busy) { commonStart = it } }
                Box(Modifier.weight(1f)) { DateSheetTime("End", commonEnd, !busy) { commonEnd = it } }
            }
            OutlinedButton(onClick = {
                rows = rows.map { if (it["included"] != false) it + mapOf("startTime" to commonStart, "endTime" to commonEnd) else it }
                validationError = null
            }, enabled = !busy && selected.isNotEmpty() && commonStart.isNotBlank() && commonEnd.isNotBlank() && commonStart < commonEnd, modifier = Modifier.fillMaxWidth()) { Text("Apply to Selected Papers") }
            Text("${selected.size} papers selected", color = ExamMuted, style = MaterialTheme.typography.bodySmall)
        }
        rows.forEach { row ->
            val id = row.examText("subjectId")
            val included = row["included"] != false
            ExamCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.examText("subject"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Checkbox(checked = included, onCheckedChange = { change(id, "included", it) }, enabled = !busy)
                }
                if (included) {
                    ExamDate("Paper Date", row.examText("date"), !busy) { change(id, "date", it) }
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) { DateSheetTime("Start", row.examText("startTime"), !busy) { change(id, "startTime", it) } }
                        Box(Modifier.weight(1f)) { DateSheetTime("End", row.examText("endTime"), !busy) { change(id, "endTime", it) } }
                    }
                }
            }
        }
        OutlinedTextField(instructions, { instructions = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Datesheet Instructions (optional)") })
        (validationError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching {
                require(sessionId.isNotBlank() && classId.isNotBlank() && sectionId.isNotBlank()) { "Select an academic session, class and section." }
                val papers = DateSheetPolicy.selectedEntries(rows)
                DateSheetPolicy.validate(term, papers)
                papers
            }.onSuccess { papers ->
                validationError = null
                onSave(DiaryItem(entry.id, entry.raw + mapOf("academicSessionId" to sessionId, "targetClassId" to classId, "targetSectionId" to sectionId, "assessmentType" to term, "datesheetEntries" to papers, "description" to instructions)))
            }.onFailure { validationError = it.message ?: "Check the datesheet details." }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save Datesheet") }
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun DateSheetTime(label: String, value: String, enabled: Boolean, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    TextButton(onClick = {
        val time = value.split(":"); val hour = time.getOrNull(0)?.toIntOrNull() ?: 9; val minute = time.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(context, { _, h, m -> onSelect(String.format(Locale.US, "%02d:%02d", h, m)) }, hour, minute, true).show()
    }, enabled = enabled) { Text("$label: ${value.ifBlank { "Select time" }}") }
}

@Composable
private fun DateSheetDetailsDialog(sheet: DiaryItem, onClose: () -> Unit, onPrint: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Term Datesheet") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(sheet.text("name"), fontWeight = FontWeight.Bold)
            Text("Session: ${sheet.text("academicSession")}", color = ExamGold)
            DiaryPolicy.maps(sheet.raw["datesheetEntries"]).forEach { row ->
                Text(row.examText("subject"), fontWeight = FontWeight.Bold)
                Text("${row.examText("date")} • ${row.examText("startTime")} – ${row.examText("endTime")}")
                HorizontalDivider()
            }
            if (sheet.text("description").isNotBlank()) Text(sheet.text("description"))
        }
    }, confirmButton = { TextButton(onClick = onPrint) { Text("A4 Print / Save PDF") } }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}


