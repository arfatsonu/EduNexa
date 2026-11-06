package pk.edunexa.app.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pk.edunexa.app.attendance.AdminAttendanceRepository
import pk.edunexa.app.attendance.AttendanceBlock
import pk.edunexa.app.attendance.AttendanceOverview
import pk.edunexa.app.attendance.AttendanceStudent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val AInk: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
private val AGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val ABorder = Color(0xFFE2E8F0)
private val AMuted = Color(0xFF64748B)
private val ALight = Color(0xFF94A3B8)
private val AGreen = Color(0xFF16A34A)
private val AGreenSoft = Color(0xFFF0FDF4)
private val ARed = Color(0xFFDC2626)
private val ARedSoft = Color(0xFFFFF1F2)
private val AAmber = Color(0xFFD97706)
private val AAmberSoft = Color(0xFFFEF3E2)
private val AOrange = Color(0xFFEA580C)
private val AOrangeSoft = Color(0xFFFFF7ED)
private val ABlue = Color(0xFF1D4ED8)
private val ABlueSoft = Color(0xFFEFF6FF)

private sealed interface AdminAttendanceState {
    data object Loading : AdminAttendanceState
    data class Ready(val overview: AttendanceOverview) : AdminAttendanceState
    data class Error(val message: String) : AdminAttendanceState
}

@Composable
fun AdminAttendanceScreen(
    schoolId: String,
    activeSessionId: String,
    activeSessionName: String,
    adminName: String,
    modifier: Modifier = Modifier,
    actor: pk.edunexa.app.auth.PortalSession? = null
) {
    val repository = remember { AdminAttendanceRepository() }
    var selectedDate by remember { mutableStateOf(todayKey()) }
    var refresh by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<AdminAttendanceState>(AdminAttendanceState.Loading) }
    var absentOnly by remember { mutableStateOf(false) }
    var detailBlock by remember { mutableStateOf<AttendanceBlock?>(null) }
    var overrideBlock by remember { mutableStateOf<AttendanceBlock?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(notice) { notice = null }

    LaunchedEffect(schoolId, activeSessionId, selectedDate, refresh) {
        state = AdminAttendanceState.Loading
        repository.loadOverview(
            schoolId = schoolId,
            academicSessionId = activeSessionId,
            date = selectedDate
        ) { result ->
            state = result.fold(
                onSuccess = {
                    detailBlock = detailBlock?.let { selected -> it.blocks.firstOrNull { fresh -> fresh.key == selected.key } }
                    AdminAttendanceState.Ready(it)
                },
                onFailure = { AdminAttendanceState.Error(it.message ?: "Could not load attendance.") }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        actor?.let { CardAttendanceScanner(it, onSaved = { selectedDate = pk.edunexa.app.attendance.CardAttendancePolicy.today(); refresh++ }) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Attendance Overview",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Admin monitoring · Live Firebase",
                    fontSize = 10.sp,
                    color = AMuted
                )
            }
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Outlined.Refresh, "Refresh", tint = AInk)
            }
        }

        Spacer(Modifier.height(10.dp))

        AttendanceFilterCard(
            selectedDate = selectedDate,
            activeSessionName = activeSessionName.ifBlank { activeSessionId },
            onDateChange = {
                selectedDate = it
                notice = null
            }
        )

        Spacer(Modifier.height(9.dp))

        if (!notice.isNullOrBlank()) {
            Spacer(Modifier.height(9.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AGreenSoft,
                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
            ) {
                Text(
                    notice.orEmpty(),
                    Modifier.padding(10.dp),
                    color = AGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when (val current = state) {
            AdminAttendanceState.Loading -> {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AGold)
                }
            }

            is AdminAttendanceState.Error -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ARedSoft,
                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(current.message, color = ARed, fontSize = 11.sp)
                        TextButton(onClick = { refresh++ }) {
                            Text("Retry")
                        }
                    }
                }
            }

            is AdminAttendanceState.Ready -> {
                val overview = current.overview
                AttendanceOverviewSummary(overview)
                OutlinedButton(onClick={ absentOnly=!absentOnly },modifier=Modifier.fillMaxWidth()) { Text(if(absentOnly) "Show class overview" else "Absent follow-up • ${overview.absent} students") }
                if(absentOnly) {
                    Text("${selectedDate} • Saved absences only. Unmarked students are not counted as absent.",style=MaterialTheme.typography.bodySmall)
                    overview.blocks.forEach { b -> b.students.filter { b.entries[it.id]?.status == "Absent" }.forEach { pupil ->
                        OutlinedButton(onClick={ detailBlock=b },modifier=Modifier.fillMaxWidth()) { Text("${pupil.fullName} • ${b.className} ${b.sectionName} • Call / WhatsApp / Reason") }
                    } }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Class Attendance Status",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            selectedDate + " · " + overview.marked.toString() +
                                " marked · " + (overview.pending + overview.incomplete).toString() +
                                " require attention",
                            fontSize = 10.sp,
                            color = AMuted
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, ABorder)
                    ) {
                        Text(
                            overview.blocks.size.toString() + " Blocks",
                            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AMuted
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (overview.blocks.isEmpty()) {
                    AttendanceEmpty("No active classes/sections configured.")
                } else {
                    overview.blocks.forEach { block ->
                        AttendanceBlockCard(
                            block = block,
                            onDetails = { detailBlock = block },
                            onOverride = {
                                if (block.status == "Pending" || block.status == "Incomplete") {
                                    overrideBlock = block
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }

    detailBlock?.let { block ->
        AttendanceDetailsDialog(
            onlyAbsent = absentOnly,
            schoolId = schoolId,
            activeSessionId = activeSessionId,
            selectedDate = selectedDate,
            block = block,
            repository = repository,
            adminName = adminName,
            onDismiss = { detailBlock = null },
            onFollowUpSaved = {
                notice = "The absent student's follow-up note has been saved."
                refresh++
            }
        )
    }

    overrideBlock?.let { block ->
        AdminOverrideDialog(
            schoolId = schoolId,
            activeSessionId = activeSessionId,
            selectedDate = selectedDate,
            block = block,
            repository = repository,
            adminName = adminName,
            onDismiss = { overrideBlock = null },
            onSaved = {
                overrideBlock = null
                notice = block.className + " · " + block.sectionName +
                    " attendance saved using Admin Override."
                refresh++
            }
        )
    }
}

@Composable
private fun AttendanceFilterCard(
    selectedDate: String,
    activeSessionName: String,
    onDateChange: (String) -> Unit
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ABorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(activeSessionName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("All Classes & Sections", fontSize = 10.sp, color = AMuted)
            }
            TextButton(onClick = {
                val cal = parseDate(selectedDate)
                DatePickerDialog(context, { _, y, m, d ->
                    onDateChange(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }) {
                Icon(Icons.Outlined.CalendarMonth, "Change attendance date", Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(selectedDate, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PortalInfoBox(
    modifier: Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, ABorder)
    ) {
        Column(Modifier.padding(9.dp)) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = ALight)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AttendanceOverviewSummary(overview: AttendanceOverview) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        OverviewMini("CLASSES", overview.blocks.size, AInk, Color.White)
        OverviewMini("MARKED", overview.marked, AGreen, AGreenSoft)
        OverviewMini("PENDING", overview.pending, AAmber, AAmberSoft)
        OverviewMini("INCOMPLETE", overview.incomplete, AOrange, AOrangeSoft)
        OverviewMini("STUDENTS", overview.students, AInk, Color.White)
        OverviewMini("PRESENT", overview.present, AGreen, AGreenSoft)
        OverviewMini("ABSENT", overview.absent, ARed, ARedSoft)
        OverviewMini("LEAVE", overview.leave, AAmber, AAmberSoft)
        OverviewMini("LATE", overview.late, AOrange, AOrangeSoft)
    }
}

@Composable
private fun OverviewMini(
    label: String,
    value: Int,
    tint: Color,
    background: Color
) {
    Surface(
        modifier = Modifier.width(94.dp),
        shape = RoundedCornerShape(12.dp),
        color = background,
        border = BorderStroke(1.dp, ABorder)
    ) {
        Column(
            Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                color = AMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value.toString(),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tint
            )
        }
    }
}

@Composable
private fun AttendanceBlockCard(
    block: AttendanceBlock,
    onDetails: () -> Unit,
    onOverride: () -> Unit
) {
    val statusColors = when (block.status) {
        "Marked" -> Triple(AGreen, AGreenSoft, Color(0xFFBBF7D0))
        "Incomplete" -> Triple(AOrange, AOrangeSoft, Color(0xFFFED7AA))
        "Pending" -> Triple(AAmber, AAmberSoft, Color(0xFFFDE68A))
        else -> Triple(AMuted, Color(0xFFF8FAFC), ABorder)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, statusColors.third),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        block.className + " · " + block.sectionName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        block.total.toString() + " active students",
                        fontSize = 9.5.sp,
                        color = AMuted
                    )
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = statusColors.second,
                    border = BorderStroke(1.dp, statusColors.third)
                ) {
                    Text(
                        block.status.uppercase(Locale.US),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColors.first
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                BlockCount(Modifier.weight(1f), "TOTAL", block.total, AInk, Color(0xFFF8FAFC))
                BlockCount(Modifier.weight(1f), "Present", block.present, AGreen, AGreenSoft)
                BlockCount(Modifier.weight(1f), "Absent", block.absent, ARed, ARedSoft)
                BlockCount(Modifier.weight(1f), "Leave", block.leave, AAmber, AAmberSoft)
                BlockCount(Modifier.weight(1f), "LATE", block.late, AOrange, AOrangeSoft)
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    when (block.status) {
                        "Marked" -> {
                            Text(
                                if(pk.edunexa.app.settings.AttendancePolicy.rate(block.present, block.absent, block.late, block.leave, block.lateCountsAsPresent, block.excludeLeave) == null) "No counted entries" else "Attendance ${block.attendanceRate}% • Min ${block.minimumPercent}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (block.markedBy.isNotBlank())
                                    "Marked by " + block.markedBy
                                else "Attendance recorded",
                                fontSize = 9.sp,
                                color = AMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        "Incomplete" -> {
                            Text(
                                "Attendance Incomplete",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AOrange
                            )
                            Text(
                                (block.total - block.recorded).coerceAtLeast(0).toString() +
                                    " students unmarked",
                                fontSize = 9.sp,
                                color = AMuted
                            )
                        }
                        "Pending" -> {
                            Text(
                                "Attendance not marked yet",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AAmber
                            )
                            Text(
                                "Waiting for Class Incharge",
                                fontSize = 9.sp,
                                color = ALight
                            )
                        }
                        else -> {
                            Text(
                                "No active students",
                                fontSize = 10.sp,
                                color = AMuted
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if ((block.status == "Pending" || block.status == "Incomplete") && block.total > 0) {
                        OutlinedButton(
                            onClick = onOverride,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(13.dp), tint = AAmber)
                            Spacer(Modifier.width(3.dp))
                            Text("Admin Override", fontSize = 8.5.sp, color = AAmber)
                        }
                    }

                    OutlinedButton(
                        onClick = onDetails,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(Icons.Outlined.Search, null, Modifier.size(13.dp), tint = AInk)
                        Spacer(Modifier.width(3.dp))
                        Text("Details", fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockCount(
    modifier: Modifier,
    label: String,
    value: Int,
    tint: Color,
    background: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = background,
        border = BorderStroke(1.dp, ABorder)
    ) {
        Column(
            Modifier.padding(vertical = 7.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = tint)
            Text(value.toString(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = tint)
        }
    }
}

@Composable
private fun AttendanceDetailsDialog(
    schoolId: String,
    activeSessionId: String,
    selectedDate: String,
    block: AttendanceBlock,
    repository: AdminAttendanceRepository,
    adminName: String,
    onDismiss: () -> Unit,
    onFollowUpSaved: () -> Unit,
    onlyAbsent: Boolean = false
) {
    val context = LocalContext.current
    val currentSchoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Text(
                            block.className + " · " + block.sectionName,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Attendance Details · " + block.status,
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp
                        )
                    }
                }

                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    block.students.filter { !onlyAbsent || block.entries[it.id]?.status == "Absent" }.forEach { student ->
                        val entry = block.entries[student.id]
                        DetailStudentRow(
                            student = student,
                            status = entry?.status ?: "Unmarked",
                            remarks = listOf(entry?.remarks.orEmpty(),entry?.arrivalTime?.takeIf { it.isNotBlank() }?.let { "Arrival: $it PKT" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" • "),
                            followUpComment = entry?.followUpComment.orEmpty(),
                            followUpUpdatedBy = entry?.followUpUpdatedBy.orEmpty(),
                            onSaveFollowUp = { comment, callback ->
                                repository.saveAbsentFollowUp(
                                    schoolId = schoolId,
                                    academicSessionId = activeSessionId,
                                    date = selectedDate,
                                    block = block,
                                    studentId = student.id,
                                    comment = comment,
                                    updatedBy = adminName,
                                    onResult = callback
                                )
                            },
                            onFollowUpSaved = onFollowUpSaved,
                            onCall = {
                                val p = tel(student.guardianPhone)
                                if (p.isNotBlank()) runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p))
                                    )
                                }
                            },
                            onWhatsApp = {
                                val p = wa(student.guardianPhone)
                                if (p.isNotBlank()) {
                                    val school = currentSchoolName
                                    val st = entry?.status ?: "Absent"
                                    val msg = pk.edunexa.app.notifications.DirectMessageHelper.attendanceText(
                                        schoolName = school,
                                        studentName = student.fullName,
                                        rollNo = student.rollNo,
                                        date = selectedDate,
                                        status = st
                                    )
                                    pk.edunexa.app.notifications.DirectMessageHelper.sendWhatsApp(context, p, msg)
                                }
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                Surface(color = Color.White, shadowElevation = 5.dp) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailStudentRow(
    student: AttendanceStudent,
    status: String,
    remarks: String,
    followUpComment: String,
    followUpUpdatedBy: String,
    onSaveFollowUp: (String, (Result<Unit>) -> Unit) -> Unit,
    onFollowUpSaved: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val colors = statusColors(status)
    var followUpText by remember(student.id, followUpComment) {
        mutableStateOf(followUpComment)
    }
    var savingFollowUp by remember(student.id) { mutableStateOf(false) }
    var followUpError by remember(student.id) { mutableStateOf<String?>(null) }
    var followUpSaved by remember(student.id) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(13.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ABorder)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        student.fullName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        student.admissionNo + " · Roll " + student.rollNo,
                        fontSize = 8.5.sp,
                        color = AMuted
                    )
                    if (followUpComment.isNotBlank() || followUpSaved) {
                        Text("Reason: " + followUpComment.ifBlank { followUpText.trim() },
                            fontSize = 10.sp, color = AInk,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    if (remarks.isNotBlank()) {
                        Text(
                            "Attendance note: " + remarks,
                            fontSize = 8.5.sp,
                            color = ALight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.second
                ) {
                    Text(
                        status,
                        Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        color = colors.first,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (status == "Absent") {
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onCall,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 7.dp)
                    ) {
                        Icon(Icons.Outlined.Call, "Call", Modifier.size(15.dp), tint = AInk)
                        Spacer(Modifier.width(4.dp))
                        Text("Call", fontSize = 9.sp)
                    }
                    OutlinedButton(
                        onClick = onWhatsApp,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 7.dp)
                    ) {
                        WhatsAppIcon(Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 9.sp, color = AGreen)
                    }
                }

                if (followUpComment.isBlank() && !followUpSaved) {
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = followUpText,
                    onValueChange = {
                        followUpText = it
                        followUpError = null
                        followUpSaved = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Absence Reason / Parent Follow-up") },
                    placeholder = { Text("Enter the reason after speaking with the parent…") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !savingFollowUp,
                    textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
                    shape = RoundedCornerShape(11.dp)
                )

                if (followUpUpdatedBy.isNotBlank() && followUpComment.isNotBlank()) {
                    Text(
                        "Last saved by " + followUpUpdatedBy,
                        fontSize = 8.sp,
                        color = ALight,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                if (!followUpError.isNullOrBlank()) {
                    Text(
                        followUpError.orEmpty(),
                        color = ARed,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (followUpSaved) {
                    Text(
                        "Reason saved ✓",
                        color = AGreen,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(5.dp))
                Button(
                    onClick = {
                        if (followUpText.trim().isBlank()) {
                            followUpError = "Enter a reason or follow-up note."
                            return@Button
                        }
                        savingFollowUp = true
                        followUpError = null
                        followUpSaved = false
                        onSaveFollowUp(followUpText) { result ->
                            result.onSuccess {
                                savingFollowUp = false
                                followUpSaved = true
                                onFollowUpSaved()
                            }.onFailure {
                                savingFollowUp = false
                                followUpError = it.message ?: "Could not save the follow-up note."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !savingFollowUp
                ) {
                    if (savingFollowUp) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Save Follow-up Reason", fontSize = 9.5.sp)
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun AdminOverrideDialog(
    schoolId: String,
    activeSessionId: String,
    selectedDate: String,
    block: AttendanceBlock,
    repository: AdminAttendanceRepository,
    adminName: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var reason by remember(block.key) { mutableStateOf("") }
    var statuses by remember(block.key) {
        mutableStateOf(
            block.entries.mapValues { it.value.status }.toMutableMap()
        )
    }
    var remarks by remember(block.key) {
        mutableStateOf(
            block.entries.mapValues { it.value.remarks }.toMutableMap()
        )
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Text(
                            "Admin Override",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            block.className + " · " + block.sectionName + " · " + selectedDate,
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp
                        )
                    }
                }

                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(13.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = AAmberSoft,
                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Text(
                            "Use only when the Class Incharge could not complete attendance. Access is checked again before saving; Override is blocked if the Incharge has completed attendance.",
                            Modifier.padding(10.dp),
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp,
                            color = Color(0xFF92400E)
                        )
                    }

                    Spacer(Modifier.height(9.dp))

                    OutlinedTextField(
                        value = reason,
                        onValueChange = {
                            reason = it
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Override Reason *") },
                        placeholder = { Text("e.g. Class Incharge absent / unavailable") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(7.dp))

                    OutlinedButton(
                        onClick = {
                            val next = statuses.toMutableMap()
                            block.students.forEach { student ->
                                if (next[student.id].isNullOrBlank()) {
                                    next[student.id] = "Present"
                                }
                            }
                            statuses = next
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mark Remaining Present")
                    }

                    Spacer(Modifier.height(10.dp))

                    block.students.forEach { student ->
                        val currentStatus = statuses[student.id].orEmpty()
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, ABorder)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    student.fullName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    student.admissionNo + " · Roll " + student.rollNo,
                                    fontSize = 8.5.sp,
                                    color = AMuted
                                )

                                Spacer(Modifier.height(6.dp))

                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    listOf("Present", "Absent", "Leave", "Late").forEach { label ->
                                        FilterChip(
                                            selected = currentStatus == label,
                                            onClick = {
                                                statuses = statuses.toMutableMap().also {
                                                    it[student.id] = label
                                                }
                                                error = null
                                            },
                                            label = { Text(label, fontSize = 8.5.sp) }
                                        )
                                    }
                                }

                                if (currentStatus == "Absent" || currentStatus == "Leave" || currentStatus == "Late") {
                                    OutlinedTextField(
                                        value = remarks[student.id].orEmpty(),
                                        onValueChange = { value ->
                                            remarks = remarks.toMutableMap().also {
                                                it[student.id] = value
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Remark / Reason") },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    if (!error.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = ARedSoft,
                            border = BorderStroke(1.dp, Color(0xFFFECACA))
                        ) {
                            Text(
                                error.orEmpty(),
                                Modifier.padding(9.dp),
                                color = ARed,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Surface(color = Color.White, shadowElevation = 7.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !saving
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (reason.trim().isBlank()) {
                                    error = "An Admin Override reason is required."
                                    return@Button
                                }
                                val unmarked = block.students.count {
                                    statuses[it.id].isNullOrBlank()
                                }
                                if (unmarked > 0) {
                                    error = unmarked.toString() + " students are still unmarked."
                                    return@Button
                                }

                                saving = true
                                error = null
                                repository.saveAdminOverride(
                                    schoolId = schoolId,
                                    academicSessionId = activeSessionId,
                                    date = selectedDate,
                                    block = block,
                                    statuses = statuses,
                                    remarks = remarks,
                                    reason = reason,
                                    adminName = adminName.ifBlank { "School Administrator" }
                                ) { result ->
                                    result.onSuccess {
                                        saving = false
                                        onSaved()
                                    }.onFailure {
                                        saving = false
                                        error = it.message ?: "Could not save Admin Override attendance."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !saving
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text("Save Override")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceEmpty(message: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ABorder)
    ) {
        Text(
            message,
            Modifier.padding(22.dp),
            fontSize = 11.sp,
            color = AMuted
        )
    }
}

private fun statusColors(status: String): Pair<Color, Color> = when (status) {
    "Present" -> AGreen to AGreenSoft
    "Absent" -> ARed to ARedSoft
    "Leave" -> AAmber to AAmberSoft
    "Late" -> AOrange to AOrangeSoft
    else -> AMuted to Color(0xFFF8FAFC)
}

private fun tel(phone: String): String =
    phone.filter { it.isDigit() || it == '+' }

private fun wa(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    return when {
        digits.startsWith("92") -> digits
        digits.startsWith("0") -> "92" + digits.drop(1)
        else -> digits
    }
}

private fun todayKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())

private fun parseDate(value: String): Calendar {
    val cal = Calendar.getInstance()
    runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)
        if (parsed != null) cal.time = parsed
    }
    return cal
}




