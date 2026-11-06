package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pk.edunexa.app.R
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.dashboard.AdminDashboardData

private val DrawerNavy: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
private val DrawerGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val DrawerMuted = Color(0xFF94A3B8)
data class DrawerDestination(val label: String, val icon: ImageVector, val target: String = label)
private val AdminDestinations = listOf(
    DrawerDestination("Dashboard", Icons.Outlined.Dashboard),
    DrawerDestination("Students", Icons.Outlined.School),
    DrawerDestination("Teachers", Icons.Outlined.Groups),
    DrawerDestination("Classes & Sections", Icons.Outlined.Business),
    DrawerDestination("Subjects", Icons.Outlined.Book),
    DrawerDestination("Attendance", Icons.Outlined.CalendarMonth),
    DrawerDestination("Diary & Homework", Icons.Outlined.MenuBook),
    DrawerDestination("Tests & Exams", Icons.Outlined.Assignment),
    DrawerDestination("Results", Icons.Outlined.EmojiEvents),
    DrawerDestination("Fees", Icons.Outlined.CreditCard),
    DrawerDestination("Reports", Icons.Outlined.BarChart),
    DrawerDestination("Timetable", Icons.Outlined.Schedule),
    DrawerDestination("Excel Import", Icons.Outlined.Groups),
    DrawerDestination("Cards & Login Slips", Icons.Outlined.School),
    DrawerDestination("Activity History", Icons.Outlined.BarChart),
    DrawerDestination("Notifications", Icons.Outlined.Notifications),
    DrawerDestination("School Setup", Icons.Outlined.School),
    DrawerDestination("School Settings", Icons.Outlined.Settings),
    DrawerDestination("User Management", Icons.Outlined.AdminPanelSettings)
)

@Composable
fun PortalNavigationDrawer(
    session: PortalSession, data: AdminDashboardData, selectedTab: String,
    onNavigate: (String) -> Unit, onLogout: () -> Unit,
    content: @Composable (onMore: () -> Unit) -> Unit
) {
    RoleNavigationDrawer(session, data.schoolName, data.campusName, data.activeSessionName, "Admin", AdminDestinations, selectedTab, onNavigate, onLogout, content)
}

@Composable
fun RoleNavigationDrawer(
    session: PortalSession, schoolName: String, campusName: String, sessionName: String,
    role: String, destinations: List<DrawerDestination>, selectedTab: String,
    onNavigate: (String) -> Unit, onLogout: () -> Unit,
    content: @Composable (onMore: () -> Unit) -> Unit
) {
    val state = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.close() }; Unit }
    val width = minOf(288, (LocalConfiguration.current.screenWidthDp * 0.85f).toInt()).dp
    ModalNavigationDrawer(
        drawerState = state,
        gesturesEnabled = state.isOpen,
        scrimColor = DrawerNavy.copy(alpha = 0.8f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(width).fillMaxHeight(),
                drawerShape = RoundedCornerShape(0.dp),
                drawerContainerColor = DrawerNavy,
                drawerContentColor = Color.White,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SchoolLogo(Modifier.size(36.dp).clip(CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(schoolName, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(campusName.uppercase(), color = DrawerGold, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    IconButton(onClick = close) { Icon(Icons.Outlined.Close, "Close navigation", tint = DrawerMuted) }
                }
                HorizontalDivider(color = Color(0xFF1E293B))
                Row(Modifier.fillMaxWidth().background(Color(0xFF070A10)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(DrawerGold))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(role.uppercase() + " WORKSPACE", color = Color(0xFFE2E8F0), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if(sessionName.isNotBlank()) Text(sessionName, color = DrawerGold, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = Color(0xFF1E293B))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 10.dp)) {
                    destinations.forEach { item ->
                        DrawerMenuRow(item.label, item.icon, item.target == selectedTab) {
                            onNavigate(item.target)
                            close()
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 6.dp), color = Color(0xFF1E293B))
                    DrawerMenuRow("Logout", Icons.Outlined.Logout, false, logout = true) { close(); onLogout() }
                }
                HorizontalDivider(color = Color(0xFF1E293B))
                Row(Modifier.fillMaxWidth().background(Color(0xFF070A10)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(DrawerGold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Text(session.name.firstOrNull()?.uppercase() ?: "A", color = DrawerGold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(session.idNumber.ifBlank { role }, color = DrawerGold, fontSize = 10.sp)
                    }
                }
            }
        }
    ) { content { scope.launch { state.open() }; Unit } }
    BackHandler(enabled = state.isOpen, onBack = close)
}

@Composable
private fun DrawerMenuRow(label: String, icon: ImageVector, active: Boolean, logout: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(12.dp))
        .background(Brush.horizontalGradient(listOf(if (active) Color.White.copy(alpha = 0.10f) else Color.Transparent, Color.Transparent)))
        .clickable(onClick = onClick)) {
        if (active) Box(Modifier.align(Alignment.CenterStart).width(4.dp).height(30.dp).clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(DrawerGold))
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (logout) Color(0xFFFB7185) else if (active) DrawerGold else DrawerMuted
            Icon(icon, null, Modifier.size(18.dp), tint = tint)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), color = if (logout) tint else if (active) Color.White else DrawerMuted,
                fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
            if (active) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = DrawerGold)
        }
    }
}

