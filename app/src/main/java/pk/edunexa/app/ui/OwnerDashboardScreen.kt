package pk.edunexa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession

/** Platform workspace: deliberately contains no school dashboard or school navigation. */
@Composable
fun OwnerDashboardScreen(session: PortalSession, onLogout: () -> Unit) {
    if (session.role != PortalRole.OWNER || !session.isPlatformOwner) return
    Surface(color = Color(0xFFF5F7FB)) { Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF101C30), contentColor = Color.White) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("EduNexa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Owner workspace • ${session.name}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onLogout, colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White)) { Text("Logout") }
            }
        }
        OwnerPanelScreen(session, Modifier.weight(1f))
    }
}
}
