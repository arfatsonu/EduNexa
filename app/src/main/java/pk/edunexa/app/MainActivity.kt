package pk.edunexa.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import pk.edunexa.app.notifications.BackgroundNotificationEngine
import pk.edunexa.app.ui.CampusApp

class MainActivity : ComponentActivity() {
    private val notificationOpen = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        pk.edunexa.app.diagnostics.CrashDiagnostics.install(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Initialize 3-Layer Free Background Notification Engine
        BackgroundNotificationEngine.init(applicationContext)

        if (intent.getBooleanExtra("openNotifications", false)) notificationOpen.value++
        if (!pk.edunexa.app.diagnostics.CrashDiagnostics.showPreviousCrash(this) { showPortal() }) {
            showPortal()
        }
    }

    private fun showPortal() {
        setContent {
            CampusApp(notificationOpen.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("openNotifications", false)) notificationOpen.value++
    }
}
