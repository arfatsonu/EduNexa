package pk.edunexa.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import pk.edunexa.app.MainActivity
import pk.edunexa.app.R

class BackgroundNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val title = intent.getStringExtra("title") ?: "EduNexa Alert"
        val message = intent.getStringExtra("message") ?: "You have a new school update."
        val targetTab = intent.getStringExtra("targetTab").orEmpty()
        val notifId = intent.getIntExtra("notifId", System.currentTimeMillis().toInt())

        showNotification(context, notifId, title, message, targetTab)
    }

    companion object {
        const val CHANNEL_ID = "edunexa_school_alerts"
        const val CHANNEL_NAME = "School Alerts & Updates"

        fun showNotification(
            context: Context,
            id: Int,
            title: String,
            message: String,
            targetTab: String = ""
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Ensure High-Importance channel on Android 8.0+ (Heads-up pop up banner + sound + vibration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                var channel = manager.getNotificationChannel(CHANNEL_ID)
                if (channel == null) {
                    channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Instant notifications for attendance, fees, marks, and announcements"
                        enableLights(true)
                        lightColor = Color.BLUE
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 300, 150, 300)
                        setShowBadge(true)
                    }
                    manager.createNotificationChannel(channel)
                }
            }

            val clickIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (targetTab.isNotBlank()) {
                    putExtra("targetTab", targetTab)
                }
                putExtra("openNotifications", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                id,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_attendance_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSound(defaultSoundUri)
                .setVibrate(longArrayOf(0, 300, 150, 300))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            manager.notify(id, builder.build())
        }
    }
}
