package pk.edunexa.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
internal fun SchoolOpeningScreen() {
    val branding = LocalSchoolBranding.current
    val colors = MaterialTheme.colorScheme
    val animation = rememberInfiniteTransition(label = "School opening")
    val phase by animation.animateFloat(0f, (Math.PI * 2).toFloat(),
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "Loading dots")
    val pulse by animation.animateFloat(0.96f, 1.04f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Logo pulse")

    Box(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 28.dp)) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                SchoolLogo(Modifier.size(104.dp).graphicsLayer(scaleX = pulse, scaleY = pulse).clip(CircleShape))
            }
            Spacer(Modifier.height(20.dp))
            Text(branding.schoolName, fontSize = 20.sp, lineHeight = 27.sp,
                fontWeight = FontWeight.Bold, color = colors.primary, textAlign = TextAlign.Center)
            if(branding.campus.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(branding.campus, fontSize = 11.sp, lineHeight = 16.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.secondary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { index ->
                    val opacity = 0.25f + 0.75f * ((sin(phase - index * 0.8f) + 1f) / 2f)
                    Box(Modifier.size(6.dp).background(colors.secondary.copy(alpha = opacity), CircleShape))
                }
            }
        }
        Text("School Portal", Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.sp,
            color = colors.onBackground.copy(alpha = 0.45f))
    }
}
