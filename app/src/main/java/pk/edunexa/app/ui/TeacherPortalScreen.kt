package pk.edunexa.app.ui
import android.app.DatePickerDialog
import android.print.PrintManager
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import pk.edunexa.app.R
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.notifications.DirectMessageHelper
import pk.edunexa.app.attendance.*
import pk.edunexa.app.teachers.*
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.fees.FeePolicy
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat

private val TeacherMuted = Color(0xFF64748B)

@Composable
fun TeacherPortalScreen(session: PortalSession, onLogout: () -> Unit) {
    var page by rememberSaveable(session.uid) { mutableStateOf("Dashboard") }
    var attendanceDirty by remember { mutableStateOf(false) }
    var attendanceBusy by remember { mutableStateOf(false) }
    var pendingPage by remember { mutableStateOf<String?>(null) }
    fun navigate(target: String) {
        if(attendanceBusy) return
        if(page == "Attendance" && attendanceDirty && target != page) pendingPage = target else if(target == "Logout") onLogout() else page = target
    }
    var schoolName by remember { mutableStateOf("EduNexa") }
    DisposableEffect(session.schoolId) {
        val listener = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("schools").document(session.schoolId).addSnapshotListener { doc, e ->
            if(e == null) (doc?.get("settings") as? Map<*, *>)?.get("schoolName")?.toString()?.takeIf { it.isNotBlank() }?.let { schoolName = it }
        }
        onDispose { listener.remove() }
    }
    BackHandler(enabled = page != "Dashboard") { navigate("Dashboard") }
    RoleNavigationDrawer(session, schoolName, "School Portal", "", "Teacher",
        listOf(DrawerDestination("Dashboard", Icons.Outlined.Dashboard, "Dashboard"),
            DrawerDestination("My Classes & Students", Icons.Outlined.School, "Classes"),
            DrawerDestination("Attendance", Icons.Outlined.CalendarMonth, "Attendance"),
            DrawerDestination("Diary & Homework", Icons.Outlined.MenuBook, "Diary"),
            DrawerDestination("Tests & Marks", Icons.Outlined.Assignment, "Tests"),
            DrawerDestination("Results", Icons.Outlined.EmojiEvents, "Results"),
            DrawerDestination("Datesheets", Icons.Outlined.Event, "Datesheets"),
            DrawerDestination("My Timetable", Icons.Outlined.Schedule, "Timetable"),
            DrawerDestination("Student Questions", Icons.Outlined.Chat, "StudentMessages"),
            DrawerDestination("Notifications", Icons.Outlined.Notifications, "Notifications"),
            DrawerDestination("My Profile", Icons.Outlined.AccountCircle, "Profile")),
        page, { navigate(it) }, { navigate("Logout") }) { openMore ->
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SchoolLogo(Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(schoolName,style = MaterialTheme.typography.titleSmall,fontWeight = FontWeight.Bold,maxLines = 2)
                    Text("Teacher",style = MaterialTheme.typography.labelSmall,color = TeacherMuted)
                }
                NotificationsBell(session) { navigate("Notifications") }
                IconButton(onClick = { navigate("Profile") }) { Icon(Icons.Outlined.AccountCircle,"My profile") }
            }
        }
    }, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            listOf("Dashboard" to Icons.Outlined.Dashboard,"Attendance" to Icons.Outlined.CalendarMonth,"Diary" to Icons.Outlined.MenuBook,"Tests" to Icons.Outlined.Assignment,"More" to Icons.Outlined.Menu).forEach { (name,icon) ->
                NavigationBarItem(selected = page == name || (name == "More" && page in setOf("Classes","Profile","Notifications","Datesheets","Results","Timetable","StudentMessages")),onClick = { if(name == "More") openMore() else navigate(name) },icon = { Icon(icon,name) },label = { Text(name,style = MaterialTheme.typography.labelSmall) })
            }
        }
    }) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when(page) {
            "Dashboard" -> TeacherHomeScreen(session,modifier) { navigate(it) }
            "Attendance" -> TeacherAttendanceScreen(session,modifier,onDirtyChange = { attendanceDirty = it },onBusyChange = { attendanceBusy = it })
            "Diary" -> TeacherDiaryScreen(session,modifier)
            "Tests","Results","Datesheets" -> key(page) { ExamScreen(session,initialTab = if(page == "Tests") "Tests" else page,modifier = modifier) }
            "Notifications" -> NotificationsScreen(session,modifier)
            "Timetable" -> TimetableScreen(session = session, modifier = modifier)
            "StudentMessages" -> StudentTeacherMessagingScreen(session = session, modifier = modifier)
            else -> TeacherInformationScreen(session,page,onLogout,modifier)
        }
    }
    pendingPage?.let { target -> AlertDialog(onDismissRequest = { pendingPage = null },title = { Text("Discard unsaved attendance?") },text = { Text("Save attendance before leaving, or discard your changes.") },confirmButton = { TextButton(onClick = { pendingPage = null; attendanceDirty = false; if(target == "Logout") onLogout() else page = target }) { Text("Discard") } },dismissButton = { TextButton(onClick = { pendingPage = null }) { Text("Keep Editing") } }) }
    }

}

@Composable
private fun TeacherHomeScreen(session: PortalSession, modifier: Modifier, onNavigate: (String) -> Unit) {
    val repo = remember(session.uid) { TeacherDashboardRepository(session) }
    var data by remember { mutableStateOf<TeacherDashboardData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    DisposableEffect(session.uid) {
        val root = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("schools").document(session.schoolId)
        var initial = true
        val registration = root.collection("teachers").document(session.profileId!!).addSnapshotListener { _, e ->
            if(e == null) { if(!initial) refresh++; initial = false }
        }
        onDispose { registration.remove() }
    }
    LaunchedEffect(session.uid,refresh) {
        busy = true
        runCatching { repo.load() }.onSuccess { data = it; error = null }.onFailure { error = it.message ?: "Could not load teacher dashboard." }
        busy = false
    }
    LazyColumn(modifier.padding(horizontal = 16.dp),verticalArrangement = Arrangement.spacedBy(12.dp),contentPadding = PaddingValues(vertical = 14.dp)) {
        item { TeacherPageHeader(session.name.ifBlank { "Teacher Dashboard" }, FeePolicy.today(), !busy) { refresh++ } }
        if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { item { PortalNotice(it,true); TextButton(onClick = { refresh++ }) { Text("Retry") } } }
        data?.let { d ->
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TeacherMetric("Assigned Classes",d.classScopes.size.toString(),Modifier.weight(1f)){onNavigate("Classes")}
                TeacherMetric("Subjects",d.assignments.map { it["subjectId"] }.distinct().size.toString(),Modifier.weight(1f)){onNavigate("Classes")}
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TeacherMetric("Homework Pending",d.pendingHomework.toString(),Modifier.weight(1f)){onNavigate("Diary")}
                TeacherMetric("Marks Pending",d.pendingTests.size.toString(),Modifier.weight(1f)){onNavigate("Tests")}
            } }
            item { TeacherCard {
                Text("Quick Actions",fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigate("Diary") },modifier = Modifier.weight(1f)) { Text("Diary / Homework") }
                    OutlinedButton(onClick = { onNavigate("Tests") },modifier = Modifier.weight(1f)) { Text("Tests / Marks") }
                }
                if(d.access.incharge) OutlinedButton(onClick = { onNavigate("Attendance") },modifier = Modifier.fillMaxWidth()) { Text("Mark Class Attendance") }
            } }
            if(d.access.incharge) {
                val totalStudents = d.attendance.students.size
                val entries = d.attendance.entries.values
                val presentCount = entries.count { it.text("status") == "Present" }
                val absentCount = entries.count { it.text("status") == "Absent" }
                val leaveCount = entries.count { it.text("status") in setOf("Leave", "Short Leave") }
                val markedCount = entries.count { it.text("status") in TeacherPortalPolicy.attendanceStatuses }
                val unmarkedCount = (totalStudents - markedCount).coerceAtLeast(0)

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Class Attendance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(d.access.name("classes", d.access.inchargeClass) + " • " + d.access.name("sections", d.access.inchargeSection), color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (unmarkedCount == 0 && totalStudents > 0) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        if (unmarkedCount == 0 && totalStudents > 0) "Marked ✔" else "$unmarkedCount Unmarked",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (unmarkedCount == 0 && totalStudents > 0) Color(0xFF15803D) else Color(0xFFB45309)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Present", fontSize = 11.sp, color = Color(0xFF15803D), fontWeight = FontWeight.SemiBold)
                                        Text("$presentCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFEF2F2),
                                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Absent", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                                        Text("$absentCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Leave", fontSize = 11.sp, color = Color(0xFFD97706), fontWeight = FontWeight.SemiBold)
                                        Text("$leaveCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                    }
                                }
                            }

                            Button(
                                onClick = { onNavigate("Attendance") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (unmarkedCount == 0 && totalStudents > 0) "View Attendance & Parent Follow-up" else "Mark Class Attendance")
                            }
                        }
                    }
                }
            }
            item { TeacherCard {
                Text("Today's Work",fontWeight = FontWeight.Bold)
                Text(d.todayDiary.size.toString() + " diary entries • " + d.todayTests.size + " tests")
                if(d.todayTests.isEmpty()) Text("No assigned tests scheduled today.",color = TeacherMuted)
                d.todayTests.forEach { test -> Text(test.text("subject") + " • " + test.text("title") + " • " + test.text("startTime").ifBlank { "Time not specified" }) }
                TextButton(onClick = { onNavigate("Datesheets") }) { Text("View Class Datesheets") }
            } }
            item { TeacherCard {
                Text("My Assignments",fontWeight = FontWeight.Bold)
                if(d.assignments.isEmpty()) Text("No current subject assignments. Contact the school Admin.",color = TeacherMuted)
                d.assignments.forEach { a -> Text(d.access.name("classes",a["classId"].toString()) + " • " + d.access.name("sections",a["sectionId"].toString()) + " • " + d.access.name("subjects",a["subjectId"].toString())) }
                TextButton(onClick = { onNavigate("Classes") }) { Text("View Classes & Students") }
            } }
        }
    }
}

@Composable
private fun TeacherInformationScreen(session: PortalSession, page: String, onLogout: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var access by remember { mutableStateOf<TeacherAccess?>(null) }
    var inspectingStudent by remember { mutableStateOf<DiaryItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }; var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(session.uid,refresh) { runCatching { TeacherAccessRepository(session).load() }.onSuccess { access = it; error = null }.onFailure { error = it.message } }
    LazyColumn(modifier.padding(horizontal = 16.dp),verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding = PaddingValues(vertical = 14.dp)) {
        item { TeacherPageHeader(if(page == "Classes") "My Classes & Students" else "My Profile",if(page == "Classes") "Assigned access" else "Personal & professional details",true) { refresh++ } }
        error?.let { item { PortalNotice(it,true) } }
        val a = access
        if(a == null && error == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(a != null) {
            if(page == "Profile") {
                item { MyProfileIdentity(a.profile["fullName"]?.toString().orEmpty().ifBlank { session.name },
                    a.profile["profilePhoto"]?.toString().orEmpty().ifBlank { a.profile["photoUrl"]?.toString().orEmpty() },
                    "Teacher", a.profile["status"]?.toString().orEmpty(),
                    a.profile["employeeId"]?.toString().orEmpty().ifBlank { session.idNumber }) }
                item { MyProfileDetails("Professional Details", Icons.Outlined.Badge, listOf(
                    "Qualification" to a.profile["qualification"]?.toString().orEmpty(),
                    "Academic session" to a.sessionName,
                    "Phone" to a.profile["phone"]?.toString().orEmpty())) }
                if(a.incharge) item { MyProfileDetails("Class Incharge", Icons.Outlined.School, listOf(
                    "Class" to a.name("classes", a.inchargeClass),
                    "Section" to a.name("sections", a.inchargeSection))) }
                item { MyProfileAccount(session.email, onLogout) }
            } else {
                val scopes = (a.assignments.map { it["classId"].toString() to it["sectionId"].toString() } + if(a.incharge) listOf(a.inchargeClass to a.inchargeSection) else emptyList()).distinct()
                if(scopes.isEmpty()) item { Text("No classes assigned. Contact the school Admin.",color = TeacherMuted) }
                scopes.forEach { (cls,section) ->
                    item { Text(a.name("classes",cls) + " • " + a.name("sections",section),fontWeight = FontWeight.Bold) }
                    items(TeacherPortalPolicy.roster(a,cls,section),key = { cls + section + it.id }) { student ->
                        Card(
                            onClick = { inspectingStudent = student },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                val photo = student.text("profilePhoto").ifBlank { student.text("photoUrl") }
                                TeacherPhotoAvatar(photo, student.text("fullName"), size = 48.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(student.text("fullName"), fontWeight = FontWeight.Bold)
                                    Text(student.text("admissionNo") + " • Roll " + student.text("rollNo"), color = TeacherMuted)
                                    Text(student.text("status") + " • Tap to view profile & report", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inspectingStudent?.let { student ->
        val photo = student.text("profilePhoto").ifBlank { student.text("photoUrl") }
        val phone = student.text("guardianPhone").ifBlank { student.text("phone") }
        val schoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }
        var studentMarks by remember(student.id) { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
        var studentAttendancePct by remember(student.id) { mutableStateOf("Calculating...") }

        LaunchedEffect(student.id) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val mSnap = com.google.android.gms.tasks.Tasks.await(
                        db.collection("schools").document(session.schoolId)
                            .collection("marks").whereEqualTo("studentId", student.id).get()
                    )
                    val mList = mSnap.documents.map { it.data.orEmpty() }
                    withContext(Dispatchers.Main) { studentMarks = mList }

                    val aSnap = com.google.android.gms.tasks.Tasks.await(
                        db.collection("schools").document(session.schoolId)
                            .collection("attendance").whereEqualTo("studentId", student.id).get()
                    )
                    val total = aSnap.size()
                    val pres = aSnap.documents.count { it.getString("status") in setOf("Present", "Late") }
                    val pct = if (total > 0) "${((pres * 100) / total)}%" else "100%"
                    withContext(Dispatchers.Main) { studentAttendancePct = pct }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { inspectingStudent = null },
            title = { Text("Student Academic Profile & Report", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeacherPhotoAvatar(photo, student.text("fullName"), size = 64.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(student.text("fullName"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Roll: " + student.text("rollNo") + " • " + student.text("admissionNo"), color = TeacherMuted)
                            Text(student.text("className") + " • " + student.text("section"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text("Attendance: $studentAttendancePct", color = Color(0xFF15803D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider()

                    val father = student.text("fatherName").ifBlank { student.text("guardianName") }
                    if (father.isNotBlank()) Text("Guardian: $father", fontWeight = FontWeight.Medium)
                    if (phone.isNotBlank()) Text("Phone / WhatsApp: $phone")
                    val addr = student.text("address")
                    if (addr.isNotBlank()) Text("Address: $addr", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)

                    Text("Test Performance & Marks:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (studentMarks.isEmpty()) {
                        Text("No test records yet.", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        studentMarks.take(6).forEach { m ->
                            val obt = m["marksObtained"]?.toString() ?: "—"
                            val tot = m["totalMarks"]?.toString() ?: "—"
                            val st = m["status"]?.toString() ?: "Present"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(m["subject"]?.toString().orEmpty() + " • " + (m["testTitle"] ?: "Test"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text("$obt / $tot ($st)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (st == "Absent") Color.Red else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            printStudentAcademicReport(context, schoolName, student, studentMarks, studentAttendancePct, session.schoolId)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print Full Academic Report")
                    }

                    if (phone.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val msg = "Assalam-o-Alaikum,\nTeacher ${session.name} ($schoolName) regarding student ${student.text("fullName")} (Roll ${student.text("rollNo")}).\nAttendance: $studentAttendancePct"
                                    DirectMessageHelper.sendWhatsApp(context, phone, msg)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                WhatsAppIcon(Modifier.size(16.dp), contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("WhatsApp")
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Call")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingStudent = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun TeacherAttendanceScreen(session: PortalSession, modifier: Modifier = Modifier, onDirtyChange: (Boolean) -> Unit = {}, onBusyChange: (Boolean) -> Unit = {}) {
    val repo = remember(session.uid) { TeacherAttendanceRepository(session) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    val currentSchoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }
    var date by rememberSaveable { mutableStateOf(FeePolicy.today()) }
    var data by remember { mutableStateOf<TeacherAttendanceData?>(null) }
    var statuses by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var remarks by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var follow by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var editing by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }; var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }; var notice by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }; var dirty by remember { mutableStateOf(false) }; var discardDate by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(notice) { notice = null }
    LaunchedEffect(dirty) { onDirtyChange(dirty) }
    LaunchedEffect(busy) { onBusyChange(busy) }
    LaunchedEffect(session.uid,date,refresh) {
        loading = true
        runCatching { repo.load(date) }.onSuccess { d ->
            data = d; error = null; dirty = false
            statuses = d.students.associate { it.id to d.entries[it.id]?.text("status").orEmpty() }
            remarks = d.students.associate { it.id to d.entries[it.id]?.text("remarks").orEmpty().ifBlank { d.entries[it.id]?.text("note").orEmpty() } }
            follow = d.students.associate { it.id to d.entries[it.id]?.text("followUpComment").orEmpty() }
            editing = d.students.any { d.entries[it.id]?.text("status") !in TeacherPortalPolicy.attendanceStatuses }
        }.onFailure { error = it.message ?: "Could not load class attendance."; data = null }
        loading = false
    }
    fun pick(next: String) { if(dirty) discardDate = next else date = next }
    fun sendAbsentSms(student: DiaryItem) {
        val phone = student.text("guardianPhone").filter { it.isDigit() || it == '+' }
        if(phone.isBlank()) { error = "Guardian phone number is not available."; return }
        val msg = DirectMessageHelper.attendanceText(currentSchoolName, student.text("fullName"), student.text("rollNo"), date, statuses[student.id].orEmpty().ifBlank { "Absent" })
        val ok = DirectMessageHelper.sendSms(context, phone, msg)
        if(!ok) error = "Could not open SMS app."
    }

    fun callGuardian(student: DiaryItem) {
        val phone = student.text("guardianPhone").filter { it.isDigit() || it == '+' }
        if(phone.isBlank()) { error = "Guardian phone number is not available."; return }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        }.onFailure { error = "Could not open phone dialer." }
    }

    fun sendAbsentWhatsApp(student: DiaryItem) {
        val phone = student.text("guardianPhone").filter { it.isDigit() || it == '+' }
        if(phone.isBlank()) { error = "Guardian phone number is not available."; return }
        val msg = DirectMessageHelper.attendanceText(currentSchoolName, student.text("fullName"), student.text("rollNo"), date, statuses[student.id].orEmpty().ifBlank { "Absent" })
        val ok = DirectMessageHelper.sendWhatsApp(context, phone, msg)
        if(!ok) error = "Could not open WhatsApp. Ensure WhatsApp is installed."
    }
    Column(modifier.padding(horizontal = 16.dp)) {
        TeacherPageHeader("Class Attendance","Class-incharge access",!busy && !loading && !dirty) { refresh++ }
        OutlinedButton(onClick = {
            val cal = Calendar.getInstance()
            runCatching { SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(date)?.let { cal.time = it } }
            DatePickerDialog(context,{ _,y,m,d -> pick(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)) },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        },enabled = !busy,modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CalendarMonth,null); Text("  " + date) }
        notice?.let { PortalNotice(it) }; error?.let { PortalNotice(it,true) }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val current = data
        if(current != null && !loading) {
            if(!current.access.incharge) {
                PortalEmptyState(Icons.Outlined.CalendarMonth,"No class-incharge assignment","Your assigned subjects are available in Diary and Tests. Attendance requires a current class-incharge assignment from Admin.")
            } else {
                Text(current.access.name("classes",current.access.inchargeClass) + " • " + current.access.name("sections",current.access.inchargeSection),fontWeight = FontWeight.Bold)
                Text(statuses.values.count { it in TeacherPortalPolicy.attendanceStatuses }.toString() + " / " + current.students.size + " marked",color = TeacherMuted,style = MaterialTheme.typography.bodySmall)
                if (editing) Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { statuses = statuses.mapValues { (_,v) -> v.ifBlank { "Present" } }; dirty = true },enabled = !busy) { Text("Mark Remaining Present") }
                }
                LazyColumn(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding = PaddingValues(vertical = 10.dp)) {
                    if(current.students.isEmpty()) item { Text("No active students in this class.",color = TeacherMuted) }
                    items(current.students,key = { it.id }) { student -> TeacherCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val photo = student.text("profilePhoto").ifBlank { student.text("photoUrl") }
                            TeacherPhotoAvatar(photo, student.text("fullName"), size = 42.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(student.text("fullName"), fontWeight = FontWeight.Bold)
                                Text(student.text("admissionNo") + " • Roll " + student.text("rollNo"), color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (!editing) {
                            follow[student.id]?.takeIf { it.isNotBlank() }?.let {
                                Text("Reason: " + it, color = MaterialTheme.colorScheme.onSurface)
                            }
                            remarks[student.id]?.takeIf { it.isNotBlank() }?.let {
                                Text("Attendance note: " + it, color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TeacherPortalPolicy.attendanceStatuses.forEach { label -> FilterChip(selected = statuses[student.id] == label,onClick = { statuses = statuses + (student.id to label); dirty = true; error = null },enabled = editing && !busy,label = { Text(label) }) }
                        }
                        if(editing && statuses[student.id] in setOf("Absent","Leave","Late")) OutlinedTextField(remarks[student.id].orEmpty(),{ remarks = remarks + (student.id to it); dirty = true },Modifier.fillMaxWidth(),enabled = !busy,label = { Text("Attendance Remark / Reason") },minLines = 1)
                        if(statuses[student.id] == "Absent") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { sendAbsentSms(student) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Icon(Icons.Outlined.Message, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Message", fontSize = 11.sp, maxLines = 1)
                                }
                                OutlinedButton(onClick = { callGuardian(student) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Icon(Icons.Outlined.Call, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Call", fontSize = 11.sp, maxLines = 1)
                                }
                                OutlinedButton(onClick = { sendAbsentWhatsApp(student) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    WhatsAppIcon(Modifier.size(15.dp), contentDescription = null)
                                    Spacer(Modifier.width(3.dp))
                                    Text("WhatsApp", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                            if (editing) {
                                OutlinedTextField(follow[student.id].orEmpty(),{ follow = follow + (student.id to it); dirty = true },Modifier.fillMaxWidth(),enabled = !busy,label = { Text("Absence Reason / Parent Follow-up") },minLines = 2)
                                Text("Follow-up is saved with attendance.",color = TeacherMuted,style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } }
                }
                if (editing) Button(onClick = {
                    busy = true; error = null
                    scope.launch { runCatching { repo.save(current,statuses,remarks,follow) }.onSuccess { busy = false; dirty = false; editing = false; notice = "Attendance and follow-up saved."; refresh++ }.onFailure { busy = false; error = it.message ?: "Could not save attendance." } }
                },enabled = !busy && current.students.isNotEmpty(),modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text(if(busy) "Saving…" else "Save Attendance") }
                else OutlinedButton(onClick = { editing = true; error = null; notice = null },
                    enabled = !busy && current.students.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text("Edit") }
            }
        }
    }
    discardDate?.let { next -> AlertDialog(onDismissRequest = { discardDate = null },title = { Text("Discard unsaved attendance?") },text = { Text("Save your attendance before changing the date, or discard these changes.") },confirmButton = { TextButton(onClick = { discardDate = null; dirty = false; date = next }) { Text("Discard") } },dismissButton = { TextButton(onClick = { discardDate = null }) { Text("Keep Editing") } }) }
}

@Composable private fun TeacherCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),shape = RoundedCornerShape(16.dp),border = BorderStroke(1.dp,Color(0xFFE2E8F0)),colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp),verticalArrangement = Arrangement.spacedBy(8.dp),content = content)
    }
}
@Composable private fun TeacherPageHeader(title: String, subtitle: String, enabled: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title,style = MaterialTheme.typography.titleLarge,fontWeight = FontWeight.Bold); Text(subtitle,style = MaterialTheme.typography.bodySmall,color = TeacherMuted) }
        IconButton(onClick = onRefresh,enabled = enabled) { Icon(Icons.Outlined.Refresh,"Refresh") }
    }
}
@Composable private fun TeacherMetric(label: String, value: String, modifier: Modifier, onClick:()->Unit) {
    Card(onClick=onClick,modifier=modifier,colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) { Text(label,color = Color(0xFFCBD5E1),style = MaterialTheme.typography.labelSmall); Text(value,color = Color.White,style = MaterialTheme.typography.headlineSmall,fontWeight = FontWeight.Bold) }
    }
}


private fun printStudentAcademicReport(context: android.content.Context, schoolName: String, student: DiaryItem, marks: List<Map<String, Any?>>, attendancePct: String, schoolId: String) {
    fun esc(value: String) = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    val rows = if (marks.isEmpty()) "<tr><td colspan=\"4\">No tests recorded yet.</td></tr>"
    else marks.joinToString("") { m ->
        val sub = esc(m["subject"]?.toString().orEmpty())
        val title = esc(m["testTitle"]?.toString().orEmpty().ifBlank { "Test" })
        val obt = esc(m["marksObtained"]?.toString() ?: "—")
        val tot = esc(m["totalMarks"]?.toString() ?: "—")
        val st = esc(m["status"]?.toString() ?: "Present")
        "<tr><td>$sub</td><td>$title</td><td>$obt / $tot</td><td>$st</td></tr>"
    }

    val html = """<!doctype html><html><head><meta charset="utf-8"><style>
    @page { size: A4 portrait; margin: 15mm; }
    body { font-family: Arial, sans-serif; font-size: 11px; color: #1e293b; line-height: 1.5; }
    h1 { text-align: center; font-size: 20px; color: #0f172a; margin: 4px 0; }
    h2 { text-align: center; font-size: 14px; color: #b8860b; margin: 2px 0 16px; }
    .card { background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px; margin-bottom: 14px; }
    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
    th, td { border: 1px solid #cbd5e1; padding: 6px 8px; text-align: left; }
    th { background: #e2e8f0; font-weight: bold; }
    .sig { margin-top: 40px; display: flex; justify-content: space-between; font-size: 11px; }
    </style></head><body>
    <h1>${esc(schoolName)}</h1>
    <h2>Student Academic Progress & Performance Report</h2>
    <div class="card">
        <b>Name: ${esc(student.text("fullName"))}</b> &bull; Roll No: ${esc(student.text("rollNo"))} &bull; Admission: ${esc(student.text("admissionNo"))}<br>
        Class: ${esc(student.text("className"))} &bull; Section: ${esc(student.text("section"))}<br>
        Guardian: ${esc(student.text("fatherName").ifBlank { student.text("guardianName") })} &bull; Phone: ${esc(student.text("guardianPhone").ifBlank { student.text("phone") })}<br>
        <b>Overall Attendance: $attendancePct</b> &bull; Date: ${FeePolicy.today()}
    </div>
    <h3>Assessment & Test Results</h3>
    <table><thead><tr><th>Subject</th><th>Test Name</th><th>Marks Obtained</th><th>Status</th></tr></thead>
    <tbody>$rows</tbody></table>
    <div class="sig">
        <span>Class Incharge: ______________________</span>
        <span>Principal Signature: ______________________</span>
    </div>
    </body></html>"""

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        private var printed = false
        override fun onPageFinished(view: WebView, url: String?) {
            if (printed) return
            printed = true
            val mgr = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
            mgr.print("Student-Report-${student.text("rollNo")}", view.createPrintDocumentAdapter("Academic Report"), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asPortrait()).build())
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
