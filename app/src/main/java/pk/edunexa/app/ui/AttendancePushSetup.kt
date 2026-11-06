package pk.edunexa.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.FirebaseAuth
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.notifications.registerAttendanceDevice

@Composable
fun AttendancePushSetup(session: PortalSession) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(session.uid) {
        if (session.role == PortalRole.STUDENT) {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            FirebaseMessaging.getInstance().token.addOnSuccessListener {
                if (FirebaseAuth.getInstance().currentUser?.uid == session.uid) registerAttendanceDevice(it)
            }
        }
    }
}
