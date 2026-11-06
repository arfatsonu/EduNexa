package pk.edunexa.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import com.google.firebase.firestore.FirebaseFirestore
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.fees.FundSnapshot
import pk.edunexa.app.fees.FeePolicy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pk.edunexa.app.R
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.students.StudentDirectoryItem
import pk.edunexa.app.students.StudentDirectoryRepository
import java.text.NumberFormat
import java.util.Locale

private val SI: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
private val SG: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val SGS: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
private val SBG: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.background
private val SB = Color(0xFFE2E8F0)
private val SM = Color(0xFF64748B)
private val SLM = Color(0xFF94A3B8)
private val SGreen = Color(0xFF16A34A)
private val SGreenSoft = Color(0xFFF0FDF4)
private val SRed = Color(0xFFDC2626)
private val SRedSoft = Color(0xFFFFF1F2)

private sealed interface StudentsState {
    data object Loading : StudentsState
    data class Ready(val students: List<StudentDirectoryItem>) : StudentsState
    data class Error(val message: String) : StudentsState
}

@Composable
fun AdminStudentsScreen(
    session: PortalSession,
    activeSessionId: String,
    onFeesSaved: () -> Unit,
    schoolLabel: String,
    modifier: Modifier = Modifier,
    onBusy: (Boolean)->Unit = {}
) {
    val schoolId = session.schoolId
    val repository = remember { StudentDirectoryRepository() }
    var refresh by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<StudentsState>(StudentsState.Loading) }
    var selected by remember { mutableStateOf<StudentDirectoryItem?>(null) }
    var messageTarget by remember { mutableStateOf<StudentDirectoryItem?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<StudentDirectoryItem?>(null) }
    var deleteTarget by remember { mutableStateOf<StudentDirectoryItem?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(notice) { notice = null }
    var timelineTarget by remember { mutableStateOf<String?>(null) }
    var reportTarget by remember { mutableStateOf<String?>(null) }
    var feeTarget by remember { mutableStateOf<StudentDirectoryItem?>(null) }
    var feeRows by remember(schoolId) { mutableStateOf<List<DiaryItem>?>(null) }
    var feeConfig by remember(schoolId) { mutableStateOf<Map<String, Any?>?>(null) }
    var feesError by remember(schoolId) { mutableStateOf(false) }
    DisposableEffect(schoolId) {
        val root = FirebaseFirestore.getInstance().collection("schools").document(schoolId)
        var rowsFailed = false
        var configFailed = false
        feeRows = null; feeConfig = null; feesError = false
        val fees = root.collection("fees").addSnapshotListener { value, failure ->
            rowsFailed = failure != null; feesError = rowsFailed || configFailed
            feeRows = if(failure == null && value != null) value.documents.map { DiaryItem(it.id, it.data.orEmpty()) } else null
        }
        val config = root.collection("academicConfig").document("current").addSnapshotListener { value, failure ->
            configFailed = failure != null; feesError = rowsFailed || configFailed
            feeConfig = if(failure == null) value?.data else null
        }
        onDispose { fees.remove(); config.remove() }
    }
    fun feeView(student: StudentDirectoryItem): StudentDirectoryItem {
        val rows = feeRows; val config = feeConfig
        if(feesError) return student.copy(feeStatus = "Unavailable", feeBalanceAvailable = false, annualFundSnapshot = FundSnapshot("Unavailable", null), otherFundsSnapshot = FundSnapshot("Unavailable", null))
        if(rows == null || config == null) return student.copy(feeStatus = "Loading…", feeBalanceAvailable = false, annualFundSnapshot = FundSnapshot("Loading…", null), otherFundsSnapshot = FundSnapshot("Loading…", null))
        val sessionId = student.academicSessionId
        val sessions = DiaryPolicy.maps(config["sessions"])
        val academic = sessions.firstOrNull { sessionId.isNotBlank() && (it["id"] == sessionId || it["name"] == sessionId) }
        val aliases = setOf(sessionId, academic?.get("id")?.toString().orEmpty(), academic?.get("name")?.toString().orEmpty()).filter { it.isNotBlank() }.toSet()
        val snapshot = runCatching { FeePolicy.displaySnapshot(rows, student.id, aliases) }.getOrNull()
            ?: return student.copy(feeStatus = "Unavailable", feeBalanceAvailable = false, annualFundSnapshot = FundSnapshot("Unavailable", null), otherFundsSnapshot = FundSnapshot("Unavailable", null))
        return student.copy(feeStatus = snapshot.first, balanceDue = snapshot.second ?: 0.0, feeBalanceAvailable = snapshot.second != null,
            annualFundSnapshot = FeePolicy.fundSnapshot(rows, student.id, aliases, "Annual Fund"),
            otherFundsSnapshot = FeePolicy.fundSnapshot(rows, student.id, aliases, "Other"))
    }
    if(timelineTarget != null) { AdminStudentTimeline(session,timelineTarget!!,{timelineTarget=null},modifier);return }
    if(reportTarget != null) {
        ParentReportScreen(session, reportTarget!!, onBack = { reportTarget = null }, modifier = modifier)
        return
    }
    BackHandler(enabled = feeTarget != null) { feeTarget = null }
    if(feeTarget != null) {
        val target = feeTarget!!
        AdminFeesScreen(session, onSaved = { onFeesSaved() }, modifier = modifier,
            studentId = target.id, studentName = target.fullName,
            initialSessionId = target.academicSessionId, onBack = { feeTarget = null },onBusy=onBusy)
        return
    }

    DisposableEffect(schoolId, refresh) {
        val registration = repository.observeStudents(schoolId) { result ->
            state = result.fold(
                onSuccess = { StudentsState.Ready(it) },
                onFailure = { StudentsState.Error(it.message ?: "Could not load students.") }
            )
        }
        onDispose { registration.remove() }
    }

    when (val current = state) {
        StudentsState.Loading -> CenterMessage(
            modifier,
            "Loading students…",
            true
        )
        is StudentsState.Error -> CenterMessage(
            modifier,
            current.message,
            false,
            onRetry = { refresh += 1 }
        )
        is StudentsState.Ready -> StudentsBody(
            modifier = modifier,
            students = remember(current.students,feeRows,feeConfig,feesError) { current.students.map { feeView(it) } },
            notice = notice,
            onRefresh = { refresh += 1 },
            onAdd = {
                editorTarget = null
                editorOpen = true
            },
            onOpen = { selected = it }
        )
    }

    val profileStudent = selected?.let { picked ->
        val live = (state as? StudentsState.Ready)?.students
        if (live == null) picked else live.firstOrNull { it.id == picked.id }
    }
    profileStudent?.let { student ->
        StudentProfile(
            student = feeView(student),
            schoolLabel = schoolLabel,
            schoolId = session.schoolId,
            onReceiveFee = { selected = null; feeTarget = student },
            onCompleteReport = { selected = null; reportTarget = student.id },
            onTimeline = { selected = null;timelineTarget=student.id },
            onDismiss = { selected = null },
            onEdit = {
                selected = null
                editorTarget = student
                editorOpen = true
            },
            onDelete = {
                selected = null
                deleteTarget = student
            },
            onMessage = {
                selected = null
                messageTarget = student
            }
        )
    }

    if (editorOpen) {
        StudentEditorDialog(
            schoolId = schoolId,
            activeSessionId = activeSessionId,
            repository = repository,
            initial = editorTarget,
            onDismiss = { editorOpen = false },
            onSaved = {
                val wasEditing = editorTarget != null
                editorOpen = false
                editorTarget = null
                notice = if (wasEditing) "Student record updated successfully." else "Student added successfully."
            }
        )
    }

    deleteTarget?.let { student ->
        DeleteStudentDialog(
            student = student,
            onDismiss = { deleteTarget = null },
            onConfirm = { fail ->
                repository.deleteStudentSafely(schoolId, student.id) { result ->
                    result.onSuccess {
                        deleteTarget = null
                        notice = "Student record safely deleted."
                    }.onFailure {
                        fail(it.message ?: "Could not delete the student.")
                    }
                }
            }
        )
    }

    messageTarget?.let { student ->
        GuardianMessage(
            student = student,
            onDismiss = { messageTarget = null },
            onSend = { text, fail ->
                repository.sendGuardianMessage(
                    schoolId,
                    student,
                    "Admin",
                    text
                ) { result ->
                    result.onSuccess {
                        messageTarget = null
                        notice = "In-app message sent to ${student.fullName}."
                    }.onFailure { fail(it.message ?: "Could not send the message.") }
                }
            }
        )
    }

    @Suppress("UNUSED_VARIABLE")
    val currentAcademicSession = activeSessionId
}

@Composable
private fun StudentsBody(
    modifier: Modifier,
    students: List<StudentDirectoryItem>,
    notice: String?,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (StudentDirectoryItem) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var classFilter by remember { mutableStateOf("ALL") }
    var scope by remember { mutableStateOf("ALL") }

    val classes = remember(students) {
        students.map { it.className }.filter { it.isNotBlank() }.distinct()
            .sortedBy { Regex("\\d+").find(it)?.value?.toIntOrNull() ?: Int.MAX_VALUE }
    }
    val filtered = remember(students, search, classFilter, scope) {
        val q = search.trim().lowercase(Locale.getDefault())
        students.filter { s ->
            val textOk = q.isBlank() || listOf(
                s.fullName, s.admissionNo, s.rollNo, s.fatherName, s.guardianPhone, s.emergencyPhone
            ).any { it.lowercase(Locale.getDefault()).contains(q) }
            val classOk = classFilter == "ALL" || s.className == classFilter
            val scopeOk = when (scope) {
                "ACTIVE" -> s.status.equals("Active", true)
                "LEAVE" -> s.status.equals("On Leave", true)
                "BOYS" -> s.gender.equals("Male", true)
                "GIRLS" -> s.gender.equals("Female", true)
                else -> true
            }
            textOk && classOk && scopeOk
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        item(key = "directory_controls") {
        Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Student Directory", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SI)
                Text("Students, contacts & fee status", fontSize = 12.sp, color = SM)
            }
            OutlinedButton(
                onClick = onAdd,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Icon(Icons.Outlined.Add, "Add Student", Modifier.size(16.dp), tint = SG)
                Spacer(Modifier.width(4.dp))
                Text("Add", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SG)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, "Refresh", tint = SI)
            }
        }

        if (!notice.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SGreenSoft,
                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
            ) {
                Text(notice, Modifier.padding(10.dp), color = SGreen, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Summary("TOTAL", students.size)
            Summary("BOYS", students.count { it.gender.equals("Male", true) })
            Summary("GIRLS", students.count { it.gender.equals("Female", true) })
            Summary("NEW", students.count { it.isNewAdmission || it.admissionDate.contains("2026") })
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Search name, admission no, roll no…") },
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Filter("All Classes", classFilter == "ALL") { classFilter = "ALL" }
            classes.forEach { name ->
                Filter(name, classFilter == name) { classFilter = name }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf(
                "ALL" to "All",
                "ACTIVE" to "Active",
                "LEAVE" to "On Leave",
                "BOYS" to "Boys",
                "GIRLS" to "Girls"
            ).forEach { (key, label) ->
                Filter(label, scope == key) { scope = key }
            }
        }

        Spacer(Modifier.height(11.dp))
        Text(
            "${filtered.size} student${if (filtered.size == 1) "" else "s"}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = SM
        )
        Spacer(Modifier.height(7.dp))
        }
        }

        if (filtered.isEmpty()) {
            item(key = "empty_directory") {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, SB)
            ) {
                Column(
                    Modifier.padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.Search, null, tint = SLM)
                    Spacer(Modifier.height(6.dp))
                    Text("No matching students", fontWeight = FontWeight.Bold, color = SI)
                }
            }
            }
        } else {
            items(filtered, key = { it.id }) { s ->
                StudentCard(s) { onOpen(s) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Summary(label: String, value: Int) {
    Surface(
        modifier = Modifier.width(106.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, SB),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(26.dp), shape = RoundedCornerShape(8.dp), color = SGS) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.School, null, Modifier.size(16.dp), tint = SG)
                }
            }
            Spacer(Modifier.width(7.dp))
            Column {
                Text(value.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SI)
                Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SM)
            }
        }
    }
}

@Composable
private fun Filter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    )
}

@Composable
private fun StudentCard(student: StudentDirectoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SB),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(student, 36)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(student.fullName.ifBlank { "Unnamed Student" }, Modifier.weight(1f),
                        fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = SI,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(student.status.ifBlank { "Unknown" }, fontSize = 9.sp, lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if(student.status.equals("Active", true)) SGreen else SM)
                }
                Text(listOf(student.admissionNo, "${student.className} ${student.section}".trim(), "Roll ${student.rollNo}")
                    .filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 10.sp, lineHeight = 14.sp, color = SM, maxLines = 2)
                if(student.fatherName.isNotBlank()) Text("Father: ${student.fatherName}",
                    fontSize = 9.sp, lineHeight = 12.sp, color = SLM, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactFeeStatus("Tuition", student.feeStatus, Modifier.weight(1f))
                    if(student.annualFund > 0 || student.annualFundSnapshot.balance != null)
                        CompactFeeStatus("Annual fund", student.annualFundSnapshot.status, Modifier.weight(1f))
                    if(student.otherFunds > 0 || student.otherFundsSnapshot.balance != null)
                        CompactFeeStatus("Other funds", student.otherFundsSnapshot.status, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompactFeeStatus(category: String, status: String, modifier: Modifier = Modifier) {
    val label = status.ifBlank { "No fee record" }
    val color = when(label) {
        "Paid" -> SGreen
        "Overdue" -> SRed
        "Pending", "Partial", "Unpaid" -> SG
        else -> SM
    }
    Text(androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(androidx.compose.ui.text.SpanStyle(color = SM))
        append("$category: ")
        pop()
        pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.SemiBold))
        append(label)
        pop()
    }, modifier, fontSize = 10.sp, lineHeight = 14.sp)
}

@Composable
private fun Status(value: String) {
    val active = value.equals("Active", true)
    val leave = value.equals("On Leave", true)
    val bg = if (active) SGreenSoft else if (leave) SGS else SRedSoft
    val fg = if (active) SGreen else if (leave) SG else SRed
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(
            value.ifBlank { "Unknown" },
            Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
private fun Avatar(student: StudentDirectoryItem, size: Int) {
    val bitmap = remember(student.profilePhoto) { decodePhoto(student.profilePhoto) }
    Surface(
        Modifier.size(size.dp),
        shape = RoundedCornerShape(13.dp),
        color = SGS,
        border = BorderStroke(1.dp, Color(0xFFF7D7A1))
    ) {
        if (bitmap != null) {
            Image(
                bitmap,
                student.fullName,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    initials(student.fullName),
                    color = SG,
                    fontSize = if (size >= 60) 20.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun StudentProfile(
    student: StudentDirectoryItem,
    schoolLabel: String,
    schoolId: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMessage: () -> Unit,
    onReceiveFee: () -> Unit,
    onCompleteReport: () -> Unit,
    onTimeline: () -> Unit
) {
    val context = LocalContext.current
    var attendanceCard by remember(student.id) { mutableStateOf(false) }
    if(attendanceCard) AttendanceCardDialog(schoolId,schoolLabel,student) { attendanceCard=false }
    else Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Surface(color = SI, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(student, 62)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(student.fullName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                listOf(student.admissionNo, "${student.className}-${student.section}", "Roll ${student.rollNo}")
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Column(Modifier.padding(15.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Action(Icons.Outlined.Call, "Call", SI) {
                            val p = tel(student.guardianPhone)
                            if (p.isNotBlank()) runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$p")))
                            }
                        }
                        Action(null, "WhatsApp", SGreen) {
                            val p = wa(student.guardianPhone)
                            if (p.isNotBlank()) runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$p")))
                            }
                        }
                        Action(Icons.Outlined.Send, "Message", SG, onMessage)
                        Action(Icons.Outlined.Print, "Print", SI) {
                            StudentProfilePrinter.print(context, student, schoolLabel,schoolId)
                        }
                    }

                    Spacer(Modifier.height(13.dp))
                    OutlinedButton(onClick=onTimeline,modifier=Modifier.fillMaxWidth()){Text("Student Timeline")}
                    OutlinedButton(onClick = { attendanceCard=true },modifier=Modifier.fillMaxWidth()) { Text("QR Attendance Card") }
                    Button(onClick = onCompleteReport, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Print, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Complete Student Report")
                    }
                    Spacer(Modifier.height(10.dp))
                    Section("Student Particulars") {
                        Line("Admission No.", student.admissionNo)
                        Line("Admission Date", student.admissionDate)
                        Line("Date of Birth", student.dateOfBirth)
                        Line("Gender", student.gender)
                        Line("Blood Group", student.bloodGroup)
                        Line("B-Form / CNIC", student.bForm)
                        Line("Class & Section", "${student.className} - ${student.section}")
                        Line("Roll No.", student.rollNo)
                    }
                    Spacer(Modifier.height(9.dp))
                    Section("Guardian & Contact") {
                        Line("Father", student.fatherName)
                        Line("Occupation", student.fatherOccupation)
                        Line("Father CNIC", student.fatherCnic)
                        Line("Guardian Mobile", student.guardianPhone)
                        if (student.emergencyPhone.isNotBlank() && student.emergencyPhone != student.guardianPhone) {
                            Line("Emergency", student.emergencyPhone)
                        }
                        Line("Address", student.address)
                    }
                    Spacer(Modifier.height(9.dp))
                    Section("Fee & Portal") {
                        FeeStatusBadge(student.feeStatus, "Tuition")
                        Text("Monthly tuition • ${FeePolicy.month()}", fontSize = 11.sp, color = SM)
                        Text("Tuition includes current and earlier bills. Fund balances cover the enrollment session.", fontSize = 10.sp, color = SM)
                        Button(onClick = onReceiveFee, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.CreditCard, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Receive Fee / View Bills")
                        }
                        Line("Monthly Fee", money(student.monthlyFee))
                        if(student.feeStatus !in setOf("Loading…", "Unavailable")) Line("Saved Tuition Due", if(student.feeBalanceAvailable) money(student.balanceDue) else "—")
                        if(student.feeStatus == "Session not set") Text("Enrollment session is not set. The amount, when available, covers saved tuition bills across sessions. The enrollment session needs review.", fontSize = 10.sp, color = SM)
                        if (student.annualFund > 0 || student.annualFundSnapshot.balance != null) {
                            Line("Annual Fund Rate", money(student.annualFund))
                            FeeStatusBadge(student.annualFundSnapshot.status, "Annual fund")
                            Line("Annual Fund Pending", student.annualFundSnapshot.balance?.let { money(it) } ?: "—")
                        }
                        if (student.otherFunds > 0 || student.otherFundsSnapshot.balance != null) {
                            Line("Other Funds Rate", money(student.otherFunds))
                            FeeStatusBadge(student.otherFundsSnapshot.status, "Other funds")
                            Line("Other Funds Pending", student.otherFundsSnapshot.balance?.let { money(it) } ?: "—")
                        }
                        if (StudentProfilePrinter.looksCorrupt(student.portalEmail)) {
                            Line("Login Status", "Credential repair pending")
                            Line("Suggested Email", StudentProfilePrinter.suggestedEmail(student.admissionNo))
                            Text(
                                "The saved login email does not match the linked account. Login credentials have not been changed.",
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                color = SRed,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            Line("Portal Email", student.portalEmail)
                        }
                    }
                    if (student.remarks.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        Section("Remarks") {
                            Text(student.remarks, fontSize = 11.sp, lineHeight = 15.sp, color = SM)
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Close")
                        }
                        Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Edit, "Edit", Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Edit Details")
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Delete, "Delete", Modifier.size(16.dp), tint = SRed)
                        Spacer(Modifier.width(5.dp))
                        Text("Delete Student", color = SRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector?, label: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            Modifier.size(42.dp),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(1.dp, SB),
            shadowElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon == null) WhatsAppIcon(Modifier.size(19.dp), label)
                else Icon(icon, label, Modifier.size(19.dp), tint = tint)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 8.sp, color = SM, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Section(title: String, body: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, SB)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = SI)
            Spacer(Modifier.height(5.dp))
            body()
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp, color = SLM)
        Text(
            value,
            Modifier.padding(start = 12.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = SI,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DeleteStudentDialog(
    student: StudentDirectoryItem,
    onDismiss: () -> Unit,
    onConfirm: ((String) -> Unit) -> Unit
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.padding(18.dp)) {
                Text("Delete Student?", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SI)
                Text(student.fullName, fontSize = 11.sp, color = SG, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Text(
                    "This removes the student from the directory and disables portal access. Past attendance, fees and results are retained for school records.",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = SM
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(9.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SRedSoft,
                        border = BorderStroke(1.dp, Color(0xFFFECACA))
                    ) {
                        Text(error.orEmpty(), Modifier.padding(9.dp), color = SRed, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            busy = true
                            error = null
                            onConfirm { failure ->
                                busy = false
                                error = failure
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardianMessage(
    student: StudentDirectoryItem,
    onDismiss: () -> Unit,
    onSend: (String, (String) -> Unit) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.padding(18.dp)) {
                Text("Message Parent / Guardian", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = SI)
                Text(student.fullName, fontSize = 11.sp, color = SG, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(11.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 7,
                    enabled = !busy,
                    placeholder = { Text("Type message…") }
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error.orEmpty(), color = SRed, fontSize = 10.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f), enabled = !busy) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (text.isBlank()) error = "Enter a message."
                            else {
                                busy = true
                                onSend(text) { failure -> busy = false; error = failure }
                            }
                        },
                        Modifier.weight(1f),
                        enabled = !busy
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentsHeader(session: PortalSession, onLogout: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = Color.White, shadowElevation = 1.dp, border = BorderStroke(0.5.dp, SB)) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SchoolLogo(Modifier.size(30.dp).clip(CircleShape))
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    LocalSchoolBranding.current.schoolName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SI
                )
                Text(LocalSchoolBranding.current.campus.uppercase(), fontSize = 8.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold, color = SG)
            }
            Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFF8FAFC), border = BorderStroke(1.dp, SB)) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountCircle, null, Modifier.size(14.dp), tint = SG)
                    Spacer(Modifier.width(4.dp))
                    Text("Admin", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SG)
                }
            }
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.Notifications, "Notifications", Modifier.size(19.dp), tint = SI)
            }
            Box {
                Surface(onClick = { menu = true }, shape = CircleShape, color = SI, modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(session.name.firstOrNull()?.uppercase() ?: "A", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(session.name, fontWeight = FontWeight.Bold)
                                Text(session.email, fontSize = 10.sp, color = SM)
                            }
                        },
                        onClick = {}
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Log out", color = SRed) },
                        onClick = { menu = false; onLogout() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentsNav(onDashboard: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 7.dp, border = BorderStroke(0.5.dp, SB)) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Nav("Dashboard", Icons.Outlined.Dashboard, false, onDashboard)
            Nav("Students", Icons.Outlined.School, true, {})
            Nav("Attendance", Icons.Outlined.CalendarMonth, false, {})
            Nav("Fees", Icons.Outlined.CreditCard, false, {})
            Nav("More", Icons.Outlined.Menu, false, {})
        }
    }
}

@Composable
private fun RowScope.Nav(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, Modifier.size(if (active) 21.dp else 19.dp), tint = if (active) SG else SM)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) SG else SM)
        if (active) Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(SG))
    }
}

@Composable
private fun CenterMessage(
    modifier: Modifier,
    text: String,
    loading: Boolean,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) CircularProgressIndicator(color = SG)
        else Icon(Icons.Outlined.School, null, tint = SLM)
        Spacer(Modifier.height(10.dp))
        Text(text, color = if (loading) SM else SRed, textAlign = TextAlign.Center, fontSize = 11.sp)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

private fun decodePhoto(value: String?): ImageBitmap? {
    if (value.isNullOrBlank() || !value.startsWith("data:image")) return null
    return try {
        val encoded = value.substringAfter("base64,", "")
        if (encoded.isBlank()) null
        else {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    } catch (_: Exception) { null }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifBlank { "S" }

private fun tel(phone: String): String = phone.filter { it.isDigit() || it == '+' }

private fun wa(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    if (digits.isBlank()) return ""
    return when {
        digits.startsWith("92") -> digits
        digits.startsWith("0") -> "92" + digits.drop(1)
        else -> digits
    }
}

private fun money(value: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.maximumFractionDigits = 0
    return "Rs. " + nf.format(value)
}


@Composable
private fun FeeStatusBadge(status: String, category: String = "Fee") {
    val label = status.ifBlank { "No fee record" }
    val color = when(label) { "Paid" -> SGreen; "Overdue" -> SRed; "Pending", "Partial", "Unpaid" -> SG; else -> SM }
    Surface(shape = RoundedCornerShape(7.dp), color = color.copy(alpha = 0.09f)) {
        Text("$category: $label", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}



