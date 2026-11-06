package pk.edunexa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProfileMuted = Color(0xFF64748B)
private val ProfileBorder = Color(0xFFE2E8F0)

@Composable
internal fun MyProfileIdentity(name: String, photo: String?, role: String, status: String, identifier: String) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primary, contentColor = colors.onPrimary)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            TeacherPhotoAvatar(photo, name, 58.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(role, fontSize = 11.sp, lineHeight = 15.sp, color = colors.onPrimary.copy(alpha = 0.75f))
                Text(name.ifBlank { "My Profile" }, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Text(identifier.ifBlank { "ID not set" }, fontSize = 12.sp, lineHeight = 16.sp,
                    color = colors.onPrimary.copy(alpha = 0.85f))
                Surface(color = colors.onPrimary.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(status.ifBlank { "Status not set" }, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun MyProfileSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, ProfileBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
internal fun MyProfileDetails(title: String, icon: ImageVector, details: List<Pair<String, String>>) {
    MyProfileSection(title, icon) {
        details.forEachIndexed { index, (label, value) ->
            if(index > 0) HorizontalDivider(color = ProfileBorder.copy(alpha = 0.55f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, Modifier.weight(0.4f), fontSize = 11.sp, lineHeight = 16.sp, color = ProfileMuted)
                Text(value.ifBlank { "Not set" }, Modifier.weight(0.6f), fontSize = 12.sp, lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
internal fun MyProfileAccount(email: String, onLogout: () -> Unit) {
    MyProfileSection("Portal Account", Icons.Outlined.Email) {
        Text("Login email", fontSize = 11.sp, lineHeight = 15.sp, color = ProfileMuted)
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(email.ifBlank { "Not set" }, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        }
        Text("For changes to your profile or login, contact the school Admin.",
            fontSize = 11.sp, lineHeight = 16.sp, color = ProfileMuted)
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Outlined.Logout, null, Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign Out", fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
