package pk.edunexa.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import pk.edunexa.app.R
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.exams.ExamPolicy
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.students.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val StudentMuted = Color(0xFF64748B)

@Composable
fun StudentPortalScreen(session: PortalSession, onLogout: () -> Unit, notificationOpen: Int = 0) {
    val repo = remember(session.uid) { StudentPortalRepository(session) }
    var page by rememberSaveable(session.uid) { mutableStateOf("Dashboard") }
    LaunchedEffect(notificationOpen) { if (notificationOpen > 0) page = "Notifications" }
    var data by remember(session.uid) { mutableStateOf<StudentPortalData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var liveError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var revision by remember { mutableStateOf(0) }
    LaunchedEffect(session.uid,refresh) {
        busy = true
        runCatching { repo.load() }.onSuccess { data = it; error = null; liveError = null }
            .onFailure { error = it.message ?: "Could not load your school records. Please retry." }
        busy = false
    }
    val activityAlerts = remember(data, session.schoolId) { data?.let { pk.edunexa.app.notifications.StudentActivityAlerts.rows(it, session.schoolId) }.orEmpty() }
    val activitySnackbar = remember(session.uid) { SnackbarHostState() }
    var seenActivityIds by remember(session.uid) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(activityAlerts, data != null) {
        if(data != null) {
            val ids = activityAlerts.map { it.id }.toSet()
            val previous = seenActivityIds
            seenActivityIds = previous.orEmpty() + ids
            if(previous != null) {
                val fresh = activityAlerts.filter { it.id !in previous }
                if(fresh.isNotEmpty()) {
                    val text = if(fresh.size == 1) fresh.first().text("title") else "${fresh.size} new activity alerts"
                    if(activitySnackbar.showSnackbar(text, "View", withDismissAction = true) == SnackbarResult.ActionPerformed) page = "Notifications"
                }
            }
        }
    }
    val profile = data?.student
    DisposableEffect(session.uid,profile?.id,profile?.text("classId"),profile?.text("sectionId")) {
        val listeners = profile?.let { repo.observe(it,{ revision++ },{ liveError = it }) }.orEmpty()
        onDispose { listeners.forEach { it.remove() } }
    }
    LaunchedEffect(revision) { if(revision > 0) { delay(500); refresh++ } }
    BackHandler(enabled = page != "Dashboard") { page = "Dashboard" }
    RoleNavigationDrawer(session, data?.schoolName ?: "EduNexa", LocalSchoolBranding.current.campus, data?.sessionLabel.orEmpty(), "Student",
        listOf(DrawerDestination("Dashboard", Icons.Outlined.Dashboard, "Dashboard"),
            DrawerDestination("Timeline",Icons.Outlined.CalendarMonth,"Timeline"),
            DrawerDestination("My Profile", Icons.Outlined.AccountCircle, "Profile"),
            DrawerDestination("Attendance", Icons.Outlined.CalendarMonth, "Attendance"),
            DrawerDestination("Diary & Homework", Icons.Outlined.MenuBook, "Diary"),
            DrawerDestination("Tests & Exams", Icons.Outlined.Assignment, "Tests"),
            DrawerDestination("Published Results", Icons.Outlined.EmojiEvents, "Results"),
            DrawerDestination("Class Timetable", Icons.Outlined.Schedule, "Timetable"),
            DrawerDestination("Ask Teacher", Icons.Outlined.Chat, "AskTeacher"),
            DrawerDestination("Datesheets", Icons.Outlined.Event, "Datesheets"),
            DrawerDestination("Fees & Receipts", Icons.Outlined.CreditCard, "Fees"),
            DrawerDestination("Complete Report / Print", Icons.Outlined.Print, "Report"),
            DrawerDestination("Notifications", Icons.Outlined.Notifications, "Notifications")),
        page, { page = it }, onLogout) { openMore ->
    Scaffold(snackbarHost = { SnackbarHost(activitySnackbar) }, containerColor = MaterialTheme.colorScheme.background,topBar = {
        Surface(color = Color.White,shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp,vertical = 8.dp),verticalAlignment = Alignment.CenterVertically) {
                SchoolLogo(Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(data?.schoolName ?: "EduNexa",style = MaterialTheme.typography.titleSmall,fontWeight = FontWeight.Bold,maxLines = 2)
                    Text(LocalSchoolBranding.current.campus, style = MaterialTheme.typography.labelSmall, color = StudentMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                NotificationsBell(session, activityAlerts, data == null && busy) { page = "Notifications" }
                IconButton(onClick = { page = "Profile" }) { Icon(Icons.Outlined.AccountCircle,"My profile") }
            }
        }
    },bottomBar = {
        NavigationBar(containerColor = Color.White,tonalElevation = 0.dp) {
            listOf("Dashboard" to Icons.Outlined.Dashboard,"Attendance" to Icons.Outlined.CalendarMonth,"Diary" to Icons.Outlined.MenuBook,"Tests" to Icons.Outlined.Assignment,"More" to Icons.Outlined.Menu).forEach { (name,icon) ->
                NavigationBarItem(selected = page == name || (name == "More" && page in setOf("Fees","Results","Datesheets","Notifications","Profile","Report","Timeline","Timetable","AskTeacher")),onClick = { if(name == "More") openMore() else page = name },icon = { Icon(icon,name) },label = { Text(name,style = MaterialTheme.typography.labelSmall) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp,vertical = 8.dp),verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when(page) { "Dashboard" -> "Student Dashboard"; "Profile" -> "My Profile"; "Diary" -> "Diary & Homework"; "Tests" -> "Tests & Exams"; "Report" -> "My Complete Report"; else -> page },style = MaterialTheme.typography.titleLarge,fontWeight = FontWeight.Bold)
                    Text(data?.sessionLabel ?: "Your school records",style = MaterialTheme.typography.bodySmall,color = StudentMuted)
                }
                IconButton(onClick = { refresh++ },enabled = !busy) { Icon(Icons.Outlined.Refresh,"Refresh records") }
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { PortalNotice(it,true) }
            liveError?.let { PortalNotice(it,true) }
            val d = data
            if(page == "Notifications") NotificationsScreen(session,Modifier.weight(1f), activityAlerts, data == null && busy)
            else if(d != null) key(page) {
                when(page) {
                    "Dashboard" -> StudentHome(d,Modifier.weight(1f)) { page = it }
                    "Attendance" -> StudentAttendance(d,Modifier.weight(1f))
                    "Diary" -> StudentDiary(d,Modifier.weight(1f))
                    "Tests","Results" -> StudentAssessments(d,page == "Results",Modifier.weight(1f))
                    "Fees" -> StudentFees(d,Modifier.weight(1f),session.schoolId)
                    "Datesheets" -> StudentDatesheets(d,Modifier.weight(1f),session.schoolId)
                    "Report" -> StudentReport(d,Modifier.weight(1f),session.schoolId)
                    "Timetable" -> TimetableScreen(
                        session = session,
                        classId = d.student.text("classId"),
                        sectionId = d.student.text("sectionId"),
                        className = d.student.text("className"),
                        sectionName = d.student.text("section"),
                        modifier = Modifier.weight(1f)
                    )
                    "AskTeacher" -> {
                        val classSubjects = ((d.diary + d.tests).mapNotNull { item ->
                            val id = item.text("subjectId").ifBlank { item.text("subject") }
                            val name = item.text("subject")
                            if (id.isNotBlank() && name.isNotBlank()) id to name else null
                        } + listOf(
                            "Chemistry" to "Chemistry",
                            "Biology" to "Biology",
                            "Physics" to "Physics",
                            "English" to "English",
                            "Urdu" to "Urdu",
                            "Mathematics" to "Mathematics",
                            "Islamiyat" to "Islamiyat",
                            "Pak Studies" to "Pak Studies",
                            "Computer Science" to "Computer Science"
                        )).distinctBy { it.second }.sortedBy { it.second }

                        val classTeachers = ((d.diary + d.tests).mapNotNull { item ->
                            val id = item.text("teacherId").ifBlank { item.text("teacherName") }
                            val name = item.text("teacherName")
                            if (id.isNotBlank() && name.isNotBlank()) id to name else null
                        } + listOf(
                            "teacher-arfat" to "Muhammad Arfat",
                            "teacher-general" to "Subject Faculty"
                        )).distinctBy { it.second }.sortedBy { it.second }

                        StudentTeacherMessagingScreen(
                            session = session,
                            studentName = d.student.text("fullName"),
                            studentRollNo = d.student.text("rollNo"),
                            classId = d.student.text("classId"),
                            className = d.student.text("className"),
                            sectionId = d.student.text("sectionId"),
                            sectionName = d.student.text("section"),
                            availableTeachers = classTeachers,
                            availableSubjects = classSubjects,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    "Timeline" -> TimelineFeed(pk.edunexa.app.workflow.TimelinePolicy.build(d.student,d.attendance,d.fees,d.diary,d.results),Modifier.weight(1f).padding(horizontal=16.dp))
                    else -> StudentProfile(d,session,onLogout,Modifier.weight(1f))
                }
            } else if(!busy) Column(Modifier.padding(16.dp)) {
                Text("Your records are not available yet. Please retry or contact the school Admin.",color = StudentMuted)
                OutlinedButton(onClick = { refresh++ }) { Text("Retry") }
                TextButton(onClick = onLogout) { Text("Sign Out") }
            }
        }
    }
    }

}

@Composable
private fun StudentHome(d: StudentPortalData, modifier: Modifier, navigate: (String) -> Unit) {
    val monthly = StudentPortalPolicy.attendance(d.attendance.filter { d.inSession(it) },FeePolicy.today().take(7))
    val upcoming = d.currentTests.filter { it.text("date") >= FeePolicy.today() && it.text("status") !in setOf("Cancelled","Completed") }
    LazyColumn(modifier.padding(horizontal = 16.dp),verticalArrangement = Arrangement.spacedBy(12.dp),contentPadding = PaddingValues(bottom = 16.dp)) {
        item { StudentCard {
            Text("Welcome, " + d.student.text("fullName"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(d.student.text("className") + " • " + d.student.text("section") + " • Roll " + d.student.text("rollNo"),color = StudentMuted)
            Text(d.student.text("admissionNo"),style = MaterialTheme.typography.bodySmall,color = StudentMuted)
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudentMetric("Monthly Attendance",StudentPortalPolicy.attendancePercent(monthly),Modifier.weight(1f)) { navigate("Attendance") }
            StudentMetric("Homework Pending",d.pendingHomework.toString(),Modifier.weight(1f)) { navigate("Diary") }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudentMetric("Tuition Status",d.tuitionSnapshot.first,Modifier.weight(1f)) { navigate("Fees") }
            StudentMetric("Upcoming Tests",upcoming.size.toString(),Modifier.weight(1f)) { navigate("Tests") }
        } }
        item { StudentCard {
            Text("Quick Access",fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { navigate("Datesheets") },modifier = Modifier.weight(1f)) { Text("Datesheets") }
                OutlinedButton(onClick = { navigate("Results") },modifier = Modifier.weight(1f)) { Text("Results") }
            }
            TextButton(onClick = { navigate("Timeline") }, modifier = Modifier.fillMaxWidth()) { Text("View My Timeline") }
            OutlinedButton(onClick = { navigate("Report") },modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Print,null); Text("  Complete Student Report") }
        } }
        item { Text("Latest Diary",fontWeight = FontWeight.Bold) }
        if(d.currentDiary.isEmpty()) item { StudentEmpty("No diary entries yet","Your teachers' class diary will appear here.") }
        items(d.currentDiary.take(3),key = { it.id }) { entry -> StudentCard {
            Text(entry.text("subject") + " • " + entry.text("date"),fontWeight = FontWeight.Bold)
            Text(entry.text("topic"))
            Text(entry.text("homework").ifBlank { entry.text("classWork").ifBlank { entry.text("teacherNote") } },maxLines = 3,color = StudentMuted)
            TextButton(onClick = { navigate("Diary") }) { Text("View Diary & Homework") }
        } }
        item { Text("Upcoming Assessments",fontWeight = FontWeight.Bold) }
        if(upcoming.isEmpty()) item { StudentEmpty("No upcoming tests","New schedules will appear when your teacher adds them.") }
        items(upcoming.take(3),key = { it.id }) { entry -> StudentCard {
            Text(entry.text("subject") + " • " + entry.text("title"),fontWeight = FontWeight.Bold)
            Text(entry.text("date") + " • " + entry.text("startTime").ifBlank { "Time not set" },color = StudentMuted)
        } }
    }
}

@Composable private fun StudentMonthPicker(month: String, changed: (String) -> Unit) {
    fun shift(amount: Int): String {
        val format = SimpleDateFormat("yyyy-MM",Locale.US)
        val calendar = Calendar.getInstance()
        calendar.time = format.parse(month)!!
        calendar.add(Calendar.MONTH,amount)
        return format.format(calendar.time)
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { changed(shift(-1)) }) { Icon(Icons.Outlined.ChevronLeft,"Previous month") }
        Text(runCatching { SimpleDateFormat("MMMM yyyy",Locale.US).format(SimpleDateFormat("yyyy-MM",Locale.US).parse(month)!!) }.getOrDefault(month),fontWeight = FontWeight.Bold)
        IconButton(onClick = { changed(shift(1)) },enabled = month < FeePolicy.today().take(7)) { Icon(Icons.Outlined.ChevronRight,"Next month") }
    }
}
@Composable private fun StudentAttendance(d: StudentPortalData,modifier: Modifier) {
    var month by rememberSaveable { mutableStateOf(FeePolicy.today().take(7)) }
    val rows = StudentPortalPolicy.attendance(d.attendance.filter { d.inSession(it) },month)
    LazyColumn(modifier.padding(horizontal = 16.dp),verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding = PaddingValues(bottom = 16.dp)) {
        item { StudentMonthPicker(month) { month = it } }
        item { StudentCard {
            Text("Attendance: " + StudentPortalPolicy.attendancePercent(rows),fontWeight = FontWeight.Bold)
            Text(rows.size.toString() + " recorded days",color = StudentMuted)
            Text("Present: " + rows.count { it.text("status") == "Present" } + " • Absent: " + rows.count { it.text("status") == "Absent" })
            Text("Leave: " + rows.count { it.text("status") == "Leave" } + " • Late: " + rows.count { it.text("status") == "Late" })
        } }
        if(rows.isEmpty()) item { StudentEmpty("No attendance recorded","There are no saved attendance entries for this month.") }
        items(rows,key = { it.id }) { entry -> StudentCard {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) { Text(entry.text("attendanceDate"),fontWeight = FontWeight.Bold); Text(entry.text("status"),fontWeight = FontWeight.Bold,color = if(entry.text("status") == "Absent") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary) }
            if(entry.text("arrivalTime").isNotBlank()) Text("Arrival: ${entry.text("arrivalTime")} PKT",style=MaterialTheme.typography.bodySmall)
            val note = entry.text("remarks").ifBlank { entry.text("note") }
            if(note.isNotBlank()) Text(note,color = StudentMuted)
        } }
    }
}

@Composable private fun StudentDiary(d: StudentPortalData,modifier: Modifier) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("Diary") }
    var search by rememberSaveable { mutableStateOf("") }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    val rows = d.currentDiary.filter { (tab != "Homework" || it.text("homework").isNotBlank()) && (it.text("subject") + " " + it.text("topic") + " " + it.text("date")).contains(search,true) }
    LazyColumn(modifier.padding(horizontal = 16.dp),verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding = PaddingValues(bottom = 16.dp)) {
        item { PortalTabs(listOf("Diary","Homework"),tab) { tab = it } }
        item { OutlinedTextField(search,{ search = it },Modifier.fillMaxWidth(),singleLine = true,label = { Text("Search subject, topic or date") },leadingIcon = { Icon(Icons.Outlined.Search,null) }) }
        attachmentError?.let { item { PortalNotice(it,true) } }
        if(rows.isEmpty()) item { StudentEmpty("No entries found","Class diary and homework assigned by your teachers will appear here.") }
        items(rows,key = { it.id }) { entry -> StudentCard {
            Text(entry.text("subject") + " • " + entry.text("date"),fontWeight = FontWeight.Bold)
            Text(entry.text("topic"),style = MaterialTheme.typography.titleMedium)
            Text(entry.text("teacherName"),color = StudentMuted,style = MaterialTheme.typography.bodySmall)
            if(tab == "Diary") {
                if(entry.text("classWork").isNotBlank()) Text("Classwork: " + entry.text("classWork"))
                if(entry.text("teacherNote").isNotBlank()) Text("Teacher Note: " + entry.text("teacherNote"))
            }
            if(entry.text("homework").isNotBlank()) {
                Text("Homework: " + entry.text("homework"))
                Text("Due: " + entry.text("dueDate").ifBlank { "Not set" },color = StudentMuted)
                Text("Teacher Check: " + d.homeworkStatus(entry),fontWeight = FontWeight.Bold,color = MaterialTheme.colorScheme.secondary)
                val feedback = StudentPortalPolicy.map(d.check(entry)?.raw?.get("record"))
                val comment = feedback["note"]?.toString().orEmpty().ifBlank { feedback["remarks"]?.toString().orEmpty() }.ifBlank { feedback["comment"]?.toString().orEmpty() }
                if(comment.isNotBlank()) Text("Feedback: " + comment,color = StudentMuted)
            }
            var previewAttachment by remember { mutableStateOf<Map<String, Any?>?>(null) }
            previewAttachment?.let { AttachmentPreviewDialog(it) { previewAttachment = null } }
            val url = entry.attachment["url"]?.toString().orEmpty()
            if(url.isNotBlank()) OutlinedButton(onClick = {
                if (url.startsWith("https://")) {
                    val uri = Uri.parse(url)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,uri)) }.onFailure { attachmentError = "No compatible app is available to open this attachment." }
                } else if (url.startsWith("data:")) {
                    previewAttachment = entry.attachment
                } else {
                    attachmentError = "This attachment link is unavailable. Contact your teacher."
                }
            }) { Icon(Icons.Outlined.AttachFile,null); Text("  " + entry.attachment["name"]?.toString().orEmpty().ifBlank { "Open Attachment" }) }
        } }
    }
}

@Composable private fun StudentAssessments(d: StudentPortalData,results: Boolean,modifier: Modifier) {
    var type by rememberSaveable { mutableStateOf("All") }
    var search by rememberSaveable { mutableStateOf("") }
    val rows = (if(results) d.currentResults else d.currentTests).filter { entry ->
        val test = d.tests.firstOrNull { it.id == entry.text("testId") }
        val kind = entry.text("assessmentType").ifBlank { test?.text("assessmentType").orEmpty() }
        (type == "All" || kind == type) && (entry.text("subject") + entry.text("title") + entry.text("testTitle")).contains(search,true)
    }
    LazyColumn(modifier.padding(horizontal = 16.dp),verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("All") + ExamPolicy.types).forEach { label -> FilterChip(selected = type == label,onClick = { type = label },label = { Text(if(label == "All") label else ExamPolicy.typeLabel(label)) }) }
        } }
        item { OutlinedTextField(search,{ search = it },Modifier.fillMaxWidth(),singleLine = true,label = { Text("Search subject or test") }) }
        if(rows.isEmpty()) item { StudentEmpty(if(results) "No published results" else "No tests found",if(results) "Results appear after the school publishes them. Draft marks are private." else "Your class tests and exams will appear here.") }
        items(rows,key = { it.id }) { entry -> StudentCard {
            val test = if(results) d.tests.firstOrNull { it.id == entry.text("testId") } else entry
            Text(entry.text("subject"),fontWeight = FontWeight.Bold,style = MaterialTheme.typography.titleMedium)
            Text(entry.text("title").ifBlank { entry.text("testTitle") })
            Text(ExamPolicy.typeLabel(entry.text("assessmentType").ifBlank { test?.text("assessmentType").orEmpty() }),color = StudentMuted)
            test?.text("boardStage")?.takeIf { it.isNotBlank() }?.let { Text(it,color = StudentMuted) }
            if(results) {
                val raw = entry.raw + mapOf("totalMarks" to (entry.raw["totalMarks"] ?: test?.raw?.get("totalMarks")))
                Text("Marks: " + StudentPortalPolicy.resultScore(DiaryItem(entry.id,raw)),fontWeight = FontWeight.Bold,color = MaterialTheme.colorScheme.secondary)
                Text("Published: " + entry.text("publishedAt").take(10),color = StudentMuted,style = MaterialTheme.typography.bodySmall)
            } else {
                Text(entry.text("date") + " • " + entry.text("startTime").ifBlank { "Time not set" })
                Text("Total Marks: " + (entry.raw["totalMarks"]?.toString() ?: "Not set"),color = StudentMuted)
                Text(entry.text("status").ifBlank { "Scheduled" },fontWeight = FontWeight.Bold)
                if(entry.text("syllabus").isNotBlank()) Text("Syllabus: " + entry.text("syllabus"))
                if(entry.text("description").isNotBlank()) Text(entry.text("description"))
            }
        } }
    }
}

@Composable private fun StudentFees(d: StudentPortalData,modifier: Modifier,schoolId:String) {
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf("All") }
    val rows = d.fees.filter { d.inSession(it) && (type == "All" || it.text("feeType") == type) }.sortedByDescending { it.text("dueDate") }
    LazyColumn(modifier.padding(horizontal = 16.dp),verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding = PaddingValues(bottom = 16.dp)) {
        item { StudentCard {
            Text("Monthly Tuition: " + d.tuitionSnapshot.first,fontWeight = FontWeight.Bold)
            Text("Saved Tuition Due: " + (d.tuitionSnapshot.second?.let { money(it) } ?: "No saved bill"),color = StudentMuted)
            Text("Annual and other funds are listed separately below.",style = MaterialTheme.typography.bodySmall,color = StudentMuted)
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("All") + d.fees.map { it.text("feeType") }.filter { it.isNotBlank() }.distinct()).forEach { name -> FilterChip(selected = type == name,onClick = { type = name },label = { Text(name) }) }
        } }
        if(rows.isEmpty()) item { StudentEmpty("No saved fee bills","School-generated bills and payment receipts will appear here.") }
        items(rows,key = { it.id }) { bill -> StudentCard {
            Text(bill.text("feeType") + " • " + bill.text("month"),fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) { Text(FeePolicy.status(bill.raw),color = MaterialTheme.colorScheme.secondary,fontWeight = FontWeight.Bold); Text(money(FeePolicy.balance(bill.raw)),fontWeight = FontWeight.Bold) }
            Text("Bill: " + money(FeePolicy.amount(bill.raw,"netPayable")) + " • Paid: " + money(FeePolicy.paid(bill.raw)),color = StudentMuted)
            Text("Due: " + bill.text("dueDate") + " • " + bill.text("voucherNo"),color = StudentMuted,style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { FeePrinter.print(context,d.schoolName,bill,null,schoolId) }) { Icon(Icons.Outlined.Print,null); Text("  Print / Save Bill PDF") }
            DiaryPolicy.maps(bill.raw["paymentHistory"]).forEach { payment ->
                HorizontalDivider()
                Text(payment["receiptNo"]?.toString().orEmpty() + " • " + payment["paymentDate"]?.toString().orEmpty() + " • " + money(FeePolicy.amount(payment,"amount")),style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { FeePrinter.print(context,d.schoolName,bill,payment,schoolId) }) { Text("Print / Save Receipt PDF") }
            }
        } }
    }
}

@Composable private fun StudentDatesheets(d: StudentPortalData,modifier: Modifier,schoolId:String) {
    val context = LocalContext.current
    val sheets = d.datesheets.filter { d.inSession(it) }
    LazyColumn(modifier.padding(horizontal = 16.dp),verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding = PaddingValues(bottom = 16.dp)) {
        if(sheets.isEmpty()) item { StudentEmpty("No class datesheets","Your school's term datesheets will appear here.") }
        items(sheets,key = { it.id }) { sheet -> StudentCard {
            Text(sheet.text("name"),fontWeight = FontWeight.Bold)
            DiaryPolicy.maps(sheet.raw["datesheetEntries"]).sortedBy { it["date"].toString() }.forEach { row ->
                Text(row["subject"]?.toString().orEmpty(),fontWeight = FontWeight.SemiBold)
                Text(row["date"].toString() + " • " + row["startTime"].toString() + " – " + row["endTime"].toString(),color = StudentMuted)
            }
            if(sheet.text("description").isNotBlank()) Text(sheet.text("description"))
            OutlinedButton(onClick = { DateSheetPrinter.print(context,d.schoolName,sheet,schoolId) }) { Icon(Icons.Outlined.Print,null); Text("  Print / Save A4 PDF") }
        } }
    }
}

@Composable private fun StudentProfile(d: StudentPortalData,session: PortalSession,onLogout: () -> Unit,modifier: Modifier) {
    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)) {
        item { MyProfileIdentity(d.student.text("fullName"),
            d.student.text("profilePhoto").ifBlank { d.student.text("photoUrl") }, "Student",
            d.student.text("status"), d.student.text("admissionNo")) }
        item { MyProfileDetails("Academic Details", Icons.Outlined.School, listOf(
            "Class" to d.student.text("className"), "Section" to d.student.text("section"),
            "Roll number" to d.student.text("rollNo"), "Session" to d.sessionLabel)) }
        item { MyProfileDetails("Guardian & Contact", Icons.Outlined.Call, listOf(
            "Father" to d.student.text("fatherName"), "Guardian phone" to d.student.text("guardianPhone"))) }
        item { MyProfileAccount(session.email, onLogout) }
    }
}
@Composable private fun StudentReport(d: StudentPortalData,modifier: Modifier,schoolId:String) {
    val context = LocalContext.current
    var month by rememberSaveable { mutableStateOf(FeePolicy.today().take(7)) }
    LazyColumn(modifier.padding(horizontal = 16.dp),verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding = PaddingValues(bottom = 16.dp)) {
        item { StudentMonthPicker(month) { month = it } }
        item { StudentCard {
            Text("Complete Student Report",fontWeight = FontWeight.Bold,style = MaterialTheme.typography.titleMedium)
            Text(d.student.text("fullName") + " • " + d.student.text("admissionNo"))
            Text("Monthly attendance, homework checks, subject-wise published test results and saved fee balances.",color = StudentMuted)
            OutlinedButton(onClick = { StudentReportPrinter.print(context,d,month,schoolId) },modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Print,null); Text("  Print / Save A4 PDF") }
        } }
    }
}
private fun money(value: Double) = "Rs. " + StudentPortalPolicy.number(value)
@Composable private fun StudentCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),shape = RoundedCornerShape(16.dp),border = BorderStroke(1.dp,Color(0xFFE2E8F0)),colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp),verticalArrangement = Arrangement.spacedBy(8.dp),content = content)
    }
}
@Composable private fun StudentMetric(label: String,value: String,modifier: Modifier,onClick: () -> Unit) {
    Card(onClick = onClick,modifier = modifier,colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) { Text(label,color = Color(0xFFCBD5E1),style = MaterialTheme.typography.labelSmall); Text(value,color = Color.White,style = MaterialTheme.typography.titleLarge,fontWeight = FontWeight.Bold) }
    }
}
@Composable private fun StudentEmpty(title: String,message: String) { StudentCard { Text(title,fontWeight = FontWeight.Bold); Text(message,color = StudentMuted,style = MaterialTheme.typography.bodySmall) } }

