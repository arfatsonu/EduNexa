package pk.edunexa.app.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AttendanceMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        registerAttendanceDevice(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "School Alert"
        val body = message.notification?.body ?: message.data["body"] ?: message.data["message"] ?: "You have a new school update."
        val targetTab = message.data["targetTab"].orEmpty()
        val notifId = message.data["notifId"]?.toIntOrNull() ?: System.currentTimeMillis().toInt()

        BackgroundNotificationReceiver.showNotification(
            applicationContext,
            notifId,
            title,
            body,
            targetTab
        )
    }
}

fun registerAttendanceDevice(token: String) {
    runCatching {
        val context = FirebaseApp.getInstance().applicationContext
        val prefs = context.getSharedPreferences("edunexa_push_tokens", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
    }
}

fun clearStudentActivityNotifications() {
    runCatching {
        val context = FirebaseApp.getInstance().applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancelAll()
        WorkManager.getInstance(context).cancelAllWorkByTag("edunexa-bg-sync")
    }
}
