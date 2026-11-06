package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.edunexa.app.R
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.dashboard.AdminDashboardData
import pk.edunexa.app.dashboard.AdminDashboardRepository
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private val PortalInk: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
private val PortalGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val PortalGoldSoft: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
private val PortalBackground: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.background
private val PortalBorder = Color(0xFFE2E8F0)
private val PortalMuted = Color(0xFF64748B)
private val PortalLightMuted = Color(0xFF94A3B8)
private val PortalGreen = Color(0xFF16A34A)
private val PortalGreenSoft = Color(0xFFF0FDF4)
private val PortalRed = Color(0xFFDC2626)
private val PortalRedSoft = Color(0xFFFFF1F2)
private val PortalAmber = Color(0xFFD97706)

private sealed interface AdminDashboardUiState {
    data object Loading : AdminDashboardUiState
    data class Content(val data: AdminDashboardData) : AdminDashboardUiState
    data class Error(val message: String) : AdminDashboardUiState
}

@Composable
fun AdminDashboardScreen(
    session: PortalSession,
    onLogout: () -> Unit
) {
    val repository = remember { AdminDashboardRepository() }
    var refreshToken by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf("Dashboard") }
    BackHandler(enabled = selectedTab != "Dashboard") {
        selectedTab = "Dashboard"
    }
    var state by remember(session.schoolId) {
        mutableStateOf<AdminDashboardUiState>(AdminDashboardUiState.Loading)
    }

    var liveRevision by remember { mutableStateOf(0) }
    androidx.compose.runtime.DisposableEffect(session.schoolId,selectedTab) {
        val root = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("schools").document(session.schoolId)
        val registrations = (if(selectedTab=="Dashboard") listOf("attendance","fees","students","teachers") else emptyList()).map { name ->
            var initial = true
            root.collection(name).addSnapshotListener { _, failure ->
                if(failure == null) { if(!initial) liveRevision += 1; initial = false }
            }
        }
        onDispose { registrations.forEach { it.remove() } }
    }
    LaunchedEffect(liveRevision) {
        if(liveRevision > 0) { kotlinx.coroutines.delay(500); refreshToken += 1 }
    }

    var refreshError by remember(session.schoolId) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.DisposableEffect(session.schoolId, refreshToken) {
        var active = true
        if (state !is AdminDashboardUiState.Content) state = AdminDashboardUiState.Loading
        repository.load(session.schoolId) { result ->
            if (active) result.onSuccess {
                refreshError = null
                state = AdminDashboardUiState.Content(it)
            }.onFailure {
                val message = it.message ?: "Could not refresh the dashboard."
                if (state is AdminDashboardUiState.Content) refreshError = message
                else state = AdminDashboardUiState.Error(message)
            }
        }
        onDispose { active = false }
    }

    when (val current = state) {
        AdminDashboardUiState.Loading -> PortalDashboardLoading()
        is AdminDashboardUiState.Error -> PortalDashboardError(
            message = current.message,
            onRetry = { refreshToken += 1 },
            onLogout = onLogout
        )
        is AdminDashboardUiState.Content -> PortalAdminDashboard(
            session = session,
            data = current.data,
            onRefresh = { refreshToken += 1 },
            onLogout = onLogout,
            selectedTab = selectedTab,
            refreshError = refreshError,
            onTabChange = { tab ->
                selectedTab = tab
                if (tab == "Dashboard") refreshToken += 1
            }
        )
    }
}

@Composable
private fun PortalAdminDashboard(
    session: PortalSession,
    data: AdminDashboardData,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    selectedTab: String,
    refreshError: String?,
    onTabChange: (String) -> Unit
) {
    var operationBusy by remember{mutableStateOf(false)}
    val navigate:(String)->Unit={if(!operationBusy)onTabChange(it)}
    val logout:()->Unit={if(!operationBusy)onLogout()}
    PortalNavigationDrawer(session, data, selectedTab, navigate, logout) { onMore ->
    Scaffold(
        containerColor = PortalBackground,
        topBar = {
            PortalTopHeader(
                session = session,
                data = data,
                onLogout = logout,
                onNotifications = { navigate("Notifications") }
            )
        },
        bottomBar = { PortalBottomNav(selectedTab) { tab -> if (tab == "More") onMore() else navigate(tab) } }
    ) { innerPadding ->
        if (selectedTab == "Students") {
            AdminStudentsScreen(
                session = session,
                onFeesSaved = onRefresh,
                activeSessionId = data.activeSessionId,
                schoolLabel = "${data.schoolName} • ${data.campusName}",
                modifier = Modifier.padding(innerPadding),onBusy={operationBusy=it}
            )
        } else if (selectedTab == "Attendance") {
            AdminAttendanceScreen(
                schoolId = session.schoolId,
                activeSessionId = data.activeSessionId,
                activeSessionName = data.activeSessionName,
                adminName = session.name,
                actor = session,
                modifier = Modifier.padding(innerPadding)
            )
        } else if (selectedTab == "Fees") {
            AdminFeesScreen(session, onSaved = onRefresh, modifier = Modifier.padding(innerPadding),onBusy={operationBusy=it})
        } else if (selectedTab == "Teachers") {
            AdminTeachersScreen(
                schoolId = session.schoolId,
                activeSessionId = data.activeSessionId,
                modifier = Modifier.padding(innerPadding)
            )
        } else if (selectedTab == "Classes & Sections") {
            AdminAcademicSetupScreen(
                schoolId = session.schoolId,
                onConfigSaved = onRefresh,
                modifier = Modifier.padding(innerPadding)
            )
        } else if (selectedTab == "Subjects") {
            AdminSubjectsScreen(schoolId = session.schoolId, onConfigSaved = onRefresh, modifier = Modifier.padding(innerPadding))
        } else if (selectedTab == "Diary & Homework") {
            AdminDiaryScreen(schoolId = session.schoolId, adminName = session.name, onSaved = onRefresh, modifier = Modifier.padding(innerPadding))
        } else if (selectedTab == "Tests & Exams" || selectedTab == "Results") {
            ExamScreen(session, if (selectedTab == "Results") "Results" else "Tests", Modifier.padding(innerPadding))
        } else if (selectedTab == "Notifications") {
            NotificationsScreen(session, Modifier.padding(innerPadding))
        } else if (selectedTab == "User Management") {
            UserManagementScreen(session, navigate, Modifier.padding(innerPadding))
        } else if (selectedTab == "School Setup") {
            SchoolSetupGuide(session,data.activeTeachers,data.studentsTotal,navigate,Modifier.padding(innerPadding))
        } else if (selectedTab == "School Settings") {
            SchoolSettingsScreen(session, onRefresh, Modifier.padding(innerPadding))
        } else if (selectedTab == "Excel Import") {
            ExcelImportScreen(session,onRefresh,Modifier.padding(innerPadding).padding(horizontal=16.dp))
        } else if (selectedTab == "Cards & Login Slips") {
            BulkCardsScreen(session,data.schoolName,Modifier.padding(innerPadding))
        } else if (selectedTab == "Activity History") {
            ActivityHistoryScreen(session,Modifier.padding(innerPadding))

        } else if (selectedTab == "Reports") {
            AdminReportsScreen(session, Modifier.padding(innerPadding))
        } else if (selectedTab == "Timetable") {
            TimetableScreen(
                session = session,
                modifier = Modifier.padding(innerPadding)
            )
        } else if (selectedTab != "Dashboard") {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = "$selectedTab module coming next", color = PortalMuted)
            }
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (refreshError != null) {
                PortalNotice("$refreshError Showing previously loaded data.", true)
                TextButton(onClick = onRefresh) { Text("Retry refresh") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PortalMuted
                    )
                    Text(
                        text = "Dashboard",
                        fontSize = 22.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PortalInk
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Session",
                            fontSize = 10.sp,
                            color = PortalLightMuted
                        )
                        Text(
                            text = "  •  " + data.activeSessionName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PortalGold
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(20.dp),
                        tint = PortalInk
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MetricRow {
                PortalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "STUDENTS",
                    value = data.studentsTotal.toString(),
                    detail = buildString {
                        append(data.studentsActive)
                        append(" Active")
                        if (data.studentsOnLeave > 0) {
                            append(" · ")
                            append(data.studentsOnLeave)
                            append(" On Leave")
                        }
                    },
                    icon = Icons.Outlined.School,
                    iconTint = PortalGold,
                    iconBackground = PortalGoldSoft,
                    onClick = { navigate("Students") }
                )
                PortalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "TEACHERS",
                    value = data.activeTeachers.toString(),
                    detail = "Active Faculty",
                    icon = Icons.Outlined.Groups,
                    iconTint = PortalGold,
                    iconBackground = PortalInk,
                    onClick = { navigate("Teachers") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            MetricRow {
                PortalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "CLASSES",
                    value = data.activeClasses.toString(),
                    detail = data.activeSections.toString() + " Active Sections",
                    icon = Icons.Outlined.Business,
                    iconTint = PortalGold,
                    iconBackground = PortalGoldSoft,
                    onClick = { navigate("Classes & Sections") }
                )
                PortalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "SUBJECTS",
                    value = data.activeSubjects.toString(),
                    detail = "Curriculum Active",
                    icon = Icons.Outlined.Book,
                    iconTint = PortalGold,
                    iconBackground = PortalInk
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            MetricRow {
                PortalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "PRESENT",
                    value = data.attendance.present.toString(),
                    detail =
                        if (data.attendance.recorded > 0)
                            data.attendance.presentPercent.toString() + "% attendance"
                        else "No roll call yet",
                    icon = Icons.Outlined.CheckCircle,
                    iconTint = PortalGreen,
                    iconBackground = PortalGreenSoft,
                    valueTint = PortalInk,
                    onClick = { navigate("Attendance") }
                )
                PortalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "ABSENT",
                    value = data.attendance.absent.toString(),
                    detail =
                        if (data.attendance.recorded > 0)
                            data.attendance.leave.toString() + " on leave"
                        else "No roll call yet",
                    icon = Icons.Outlined.Warning,
                    iconTint = PortalRed,
                    iconBackground = PortalRedSoft,
                    valueTint = PortalInk,
                    onClick = { navigate("Attendance") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { navigate("Attendance") }, modifier = Modifier.weight(1f)) {
                    Text("Mark Attendance")
                }
                OutlinedButton(onClick = { navigate("Fees") }, modifier = Modifier.weight(1f)) {
                    Text("Collect Fee")
                }
            }
            Spacer(Modifier.height(12.dp))

            PortalSectionCard(
                title = "Attendance Overview",
                icon = Icons.Outlined.CalendarMonth
            ) {
                if (data.attendance.recorded == 0) {
                    PortalEmptyState(
                        title = "No attendance recorded today",
                        body = "Real attendance summary will appear here after today's roll call is saved."
                    )
                } else {
                    PortalSummaryLine("Recorded", data.attendance.recorded.toString())
                    PortalSummaryLine(
                        "Present",
                        data.attendance.present.toString() + " (" +
                            data.attendance.presentPercent.toString() + "%)",
                        PortalGreen
                    )
                    PortalSummaryLine("Absent", data.attendance.absent.toString(), PortalRed)
                    PortalSummaryLine("Leave", data.attendance.leave.toString(), PortalAmber)
                    PortalSummaryLine("Late", data.attendance.late.toString())
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            PortalSectionCard(
                title = "Fee Overview",
                subtitle = data.fees.month,
                icon = Icons.Outlined.CreditCard
            ) {
                if (!data.fees.hasData) {
                    PortalEmptyState(
                        title = "No fee records available",
                        body = "Student bills and payments will appear here once recorded."
                    )
                } else {
                    PortalSummaryLine("Tuition Billed", money(data.fees.totalBilled))
                    PortalSummaryLine("Collected", money(data.fees.totalCollected), PortalGreen)
                    PortalSummaryLine("Pending", money(data.fees.totalPending), PortalAmber)
                    PortalSummaryLine("Overdue", money(data.fees.totalOverdue), PortalRed)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 7.dp),
                        color = PortalBorder
                    )
                    PortalSummaryLine("Recovery", data.fees.recoveryRatio.toString() + "%")
                    PortalSummaryLine("Funds Collected (Session)", money(data.fees.fundsCollected), PortalGreen)
                    PortalSummaryLine("Funds Outstanding (Session)", money(data.fees.fundsPending), PortalAmber)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            UpcomingExams(session.schoolId, data.activeSessionId) { navigate("Tests & Exams") }
            RecentDiaryActivity(session.schoolId)

            Text(
                text = "Live Firebase · Last refreshed " + data.loadedAt,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = PortalLightMuted
            )

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
    }
}

@Composable
private fun PortalTopHeader(
    session: PortalSession,
    data: AdminDashboardData,
    onLogout: () -> Unit,
    onNotifications: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(0.5.dp, PortalBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SchoolLogo(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.size(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.schoolName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PortalInk
                )
                Text(
                    text = data.campusName.uppercase(),
                    fontSize = 8.sp,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.Bold,
                    color = PortalGold
                )
            }

            Surface(
                shape = RoundedCornerShape(11.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, PortalBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = PortalGold
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "Admin",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PortalGold
                    )
                }
            }

            NotificationsBell(session, onClick = onNotifications)

            Box {
                Surface(
                    onClick = { menuOpen = true },
                    shape = CircleShape,
                    color = PortalInk,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = session.name.firstOrNull()?.uppercase() ?: "A",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = session.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = session.email,
                                    fontSize = 10.sp,
                                    color = PortalMuted
                                )
                            }
                        },
                        onClick = {}
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Log out of portal",
                                color = PortalRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onLogout()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalBottomNav(selectedTab: String, onTabChange: (String) -> Unit) {
    Surface(
        color = Color.White,
        shadowElevation = 7.dp,
        border = BorderStroke(0.5.dp, PortalBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            PortalNavItem("Dashboard", Icons.Outlined.Dashboard, selectedTab == "Dashboard", { onTabChange("Dashboard") })
            PortalNavItem("Students", Icons.Outlined.School, selectedTab == "Students", { onTabChange("Students") })
            PortalNavItem("Attendance", Icons.Outlined.CalendarMonth, selectedTab == "Attendance", { onTabChange("Attendance") })
            PortalNavItem("Fees", Icons.Outlined.CreditCard, selectedTab == "Fees", { onTabChange("Fees") })
            PortalNavItem("More", Icons.Outlined.Menu, selectedTab !in listOf("Dashboard", "Students", "Attendance", "Fees"), { onTabChange("More") })
        }
    }
}

@Composable
private fun RowScope.PortalNavItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(if (active) 21.dp else 19.dp),
                tint = if (active) PortalGold else PortalMuted
            )
            if (active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(top = 24.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(PortalGold)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) PortalGold else PortalMuted
        )
    }
}

@Composable
private fun MetricRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun PortalMetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    detail: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    valueTint: Color = PortalInk,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PortalBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 9.sp,
                    letterSpacing = 0.7.sp,
                    fontWeight = FontWeight.Bold,
                    color = PortalMuted,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = iconTint
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 21.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueTint
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = detail,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PortalMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PortalSectionCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PortalBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(PortalGoldSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PortalGold
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PortalInk
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = PortalMuted
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            content()
        }
    }
}

@Composable
private fun PortalSummaryLine(
    label: String,
    value: String,
    valueColor: Color = PortalInk
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = PortalMuted
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun PortalEmptyState(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = PortalLightMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = PortalInk,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = body,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = PortalMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PortalDashboardLoading() {
    SchoolOpeningScreen()
}

@Composable
private fun PortalDashboardError(
    message: String,
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Could not load the dashboard",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = PortalInk
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            color = PortalRed
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Retry")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good Morning, Admin"
        in 12..16 -> "Good Afternoon, Admin"
        else -> "Good Evening, Admin"
    }
}

private fun money(value: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    formatter.maximumFractionDigits = 0
    return "Rs. " + formatter.format(value)
}



