package pk.edunexa.app.notifications

import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.TimeUnit

object BackgroundNotificationEngine {

    private var activeListeners = mutableListOf<ListenerRegistration>()

    fun init(context: Context) {
        schedulePeriodicWorker(context)
        startRealtimeObservers(context)
    }

    fun schedulePeriodicWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<BackgroundNotificationWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("edunexa-bg-sync")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "edunexa-background-notification-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    fun startRealtimeObservers(context: Context) {
        stopRealtimeObservers()

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        // Read user info to set up targeted listeners
        db.collection("users").document(currentUser.uid).get().addOnSuccessListener { userDoc ->
            if (!userDoc.exists() || userDoc.getBoolean("active") != true) return@addOnSuccessListener

            val schoolId = userDoc.getString("schoolId").orEmpty()
            val profileId = userDoc.getString("profileId").orEmpty()
            val role = userDoc.getString("role").orEmpty()

            if (schoolId.isBlank()) return@addOnSuccessListener

            val prefs = context.getSharedPreferences("edunexa_bg_notif_sync", Context.MODE_PRIVATE)

            // 1. Observe School Announcements
            val noticeReg = db.collection("schools").document(schoolId)
                .collection("notifications")
                .whereEqualTo("status", "Published")
                .limit(5)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    snapshot.documentChanges.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                            change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                            val doc = change.document
                            val recipientIds = (doc.get("recipientIds") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                            val isForMe = recipientIds.isEmpty() || recipientIds.contains("ALL") || recipientIds.contains(profileId)
                            if (isForMe) {
                                val notifKey = "notif_" + doc.id
                                if (!prefs.getBoolean(notifKey, false)) {
                                    val title = doc.getString("title") ?: "School Announcement"
                                    val body = doc.getString("message") ?: "New notification received."
                                    BackgroundNotificationReceiver.showNotification(
                                        context,
                                        doc.id.hashCode(),
                                        title,
                                        body,
                                        "Notifications"
                                    )
                                    prefs.edit().putBoolean(notifKey, true).apply()
                                }
                            }
                        }
                    }
                }
            activeListeners.add(noticeReg)

            // 2. Observe Student Attendance, Marks, Fees, Replies
            if (role == "student" && profileId.isNotBlank()) {
                // Attendance observer
                val attReg = db.collection("schools").document(schoolId)
                    .collection("attendance")
                    .whereEqualTo("studentId", profileId)
                    .limit(5)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) return@addSnapshotListener
                        snapshot.documentChanges.forEach { change ->
                            val doc = change.document
                            val status = doc.getString("status").orEmpty()
                            val date = doc.getString("attendanceDate") ?: doc.getString("date").orEmpty()
                            val key = "att_${doc.id}_$status"
                            if (!prefs.getBoolean(key, false) && (status.equals("Absent", ignoreCase = true) || status.equals("Late", ignoreCase = true))) {
                                BackgroundNotificationReceiver.showNotification(
                                    context,
                                    doc.id.hashCode(),
                                    "Attendance Alert: $status",
                                    "Student attendance has been marked as $status for $date.",
                                    "Attendance"
                                )
                                prefs.edit().putBoolean(key, true).apply()
                            }
                        }
                    }
                activeListeners.add(attReg)

                // Marks observer
                val marksReg = db.collection("schools").document(schoolId)
                    .collection("marks")
                    .whereEqualTo("studentId", profileId)
                    .limit(5)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) return@addSnapshotListener
                        snapshot.documentChanges.forEach { change ->
                            val doc = change.document
                            val testTitle = doc.getString("testTitle") ?: doc.getString("subject").orEmpty()
                            val marksObtained = doc.get("marksObtained")?.toString() ?: ""
                            val key = "mark_${doc.id}_$marksObtained"
                            if (marksObtained.isNotBlank() && !prefs.getBoolean(key, false)) {
                                BackgroundNotificationReceiver.showNotification(
                                    context,
                                    doc.id.hashCode(),
                                    "Marks Uploaded: $testTitle",
                                    "New test marks: $marksObtained",
                                    "Results"
                                )
                                prefs.edit().putBoolean(key, true).apply()
                            }
                        }
                    }
                activeListeners.add(marksReg)

                // Fee payment observer
                val feeReg = db.collection("schools").document(schoolId)
                    .collection("fees")
                    .whereEqualTo("studentId", profileId)
                    .limit(5)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) return@addSnapshotListener
                        snapshot.documentChanges.forEach { change ->
                            val doc = change.document
                            val lastPaymentDate = doc.getString("lastPaymentDate").orEmpty()
                            val lastVoucherNo = doc.getString("lastVoucherNo").orEmpty()
                            val paidAmount = doc.getDouble("paidAmount") ?: 0.0
                            if (lastPaymentDate.isNotBlank() && paidAmount > 0) {
                                val key = "fee_${doc.id}_${lastVoucherNo}_$lastPaymentDate"
                                if (!prefs.getBoolean(key, false)) {
                                    BackgroundNotificationReceiver.showNotification(
                                        context,
                                        doc.id.hashCode(),
                                        "Fee Payment Received ✔",
                                        "Payment recorded on $lastPaymentDate. Voucher: $lastVoucherNo",
                                        "Fees"
                                    )
                                    prefs.edit().putBoolean(key, true).apply()
                                }
                            }
                        }
                    }
                activeListeners.add(feeReg)

                // Teacher reply observer
                val msgReg = db.collection("schools").document(schoolId)
                    .collection("studentTeacherMessages")
                    .whereEqualTo("studentId", profileId)
                    .whereEqualTo("status", "Replied")
                    .limit(5)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) return@addSnapshotListener
                        snapshot.documentChanges.forEach { change ->
                            val doc = change.document
                            val teacherName = doc.getString("teacherName") ?: "Teacher"
                            val reply = doc.getString("reply").orEmpty()
                            val key = "msg_reply_${doc.id}"
                            if (reply.isNotBlank() && !prefs.getBoolean(key, false)) {
                                BackgroundNotificationReceiver.showNotification(
                                    context,
                                    doc.id.hashCode(),
                                    "Sir $teacherName Replied",
                                    reply,
                                    "AskTeacher"
                                )
                                prefs.edit().putBoolean(key, true).apply()
                            }
                        }
                    }
                activeListeners.add(msgReg)
            }
        }
    }

    fun stopRealtimeObservers() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }
}
