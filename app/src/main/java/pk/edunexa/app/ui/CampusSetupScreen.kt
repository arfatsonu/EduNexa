
package pk.edunexa.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.edunexa.app.R
import pk.edunexa.app.settings.SchoolBranding

@Composable
fun CampusSetupScreen() {
    MaterialTheme(colorScheme = schoolColorScheme(SchoolBranding())) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(painterResource(R.drawable.ic_campus_navy), "Campus", Modifier.size(84.dp))
                Spacer(Modifier.height(22.dp))
                Text("EduNexa", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Every school. Its own workspace.", Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(30.dp))
                Card(shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("School setup pending", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Your school has not been connected yet. The administrator will activate your portal before you sign in.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Muhammad Arfat", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
