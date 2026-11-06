package pk.edunexa.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BackgroundNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext Result.success()
        val prefs = applicationContext.getSharedPreferences("edunexa_bg_notif_sync", Context.MODE_PRIVATE)

        try {
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(user.uid).get(Source.SERVER).await()
            if (!userDoc.exists() || userDoc.getBoolean("active") != true) return@withContext Result.success()

            val schoolId = userDoc.getString("schoolId").orEmpty()
            val profileId = userDoc.getString("profileId").orEmpty()
            val role = userDoc.getString("role").orEmpty()

            if (schoolId.isBlank() || profileId.isBlank()) return@withContext Result.success()

            val lastSyncTime = prefs.getLong("last_sync_time", System.currentTimeMillis() - 86400000L) // last 24h default
            var maxTimeSeen = lastSyncTime

            // 1. Check School Announcements / Whole-School Notices
            val noticesSnap = runCatching {
                db.collection("schools").document(schoolId)
                    .collection("notifications")
                    .whereEqualTo("status", "Published")
                    .limit(10)
                    .get(Source.SERVER)
                    .await()
            }.getOrNull()

            noticesSnap?.documents?.forEach { doc ->
                val recipientIds = (doc.get("recipientIds") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                val isForAll = recipientIds.isEmpty() || recipientIds.contains("ALL") || recipientIds.contains(profileId)
                if (isForAll) {
                    val notifKey = "notif_" + doc.id
                    if (!prefs.getBoolean(notifKey, false)) {
                        val title = doc.getString("title") ?: "School Announcement"
                        val body = doc.getString("message") ?: "New school notification received."
                        BackgroundNotificationReceiver.showNotification(
                            applicationContext,
                            doc.id.hashCode(),
                            title,
                            body,
                            "Notifications"
                        )
                        prefs.edit().putBoolean(notifKey, true).apply()
                    }
                }
            }

            // 2. If student account, check for new attendance, fees, marks, and messages
            if (role == "student") {
                // Check recent attendance
                val attSnap = runCatching {
                    db.collection("schools").document(schoolId)
                        .collection("attendance")
                        .whereEqualTo("studentId", profileId)
                        .limit(5)
                        .get(Source.SERVER)
                        .await()
                }.getOrNull()

                attSnap?.documents?.forEach { doc ->
                    val status = doc.getString("status").orEmpty()
                    val date = doc.getString("attendanceDate") ?: doc.getString("date").orEmpty()
                    val key = "att_${doc.id}_$status"
                    if (!prefs.getBoolean(key, false)) {
                        if (status.equals("Absent", ignoreCase = true)) {
                            BackgroundNotificationReceiver.showNotification(
                                applicationContext,
                                doc.id.hashCode(),
                                "Attendance Alert: Absent",
                                "Student attendance marked as Absent for $date.",
                                "Attendance"
                            )
                        } else if (status.equals("Late", ignoreCase = true)) {
                            BackgroundNotificationReceiver.showNotification(
                                applicationContext,
                                doc.id.hashCode(),
                                "Attendance Alert: Late",
                                "Student attendance marked as Late for $date.",
                                "Attendance"
                            )
                        }
                        prefs.edit().putBoolean(key, true).apply()
                    }
                }

                // Check recent fees
                val feeSnap = runCatching {
                    db.collection("schools").document(schoolId)
                        .collection("fees")
                        .whereEqualTo("studentId", profileId)
                        .limit(5)
                        .get(Source.SERVER)
                        .await()
                }.getOrNull()

                feeSnap?.documents?.forEach { doc ->
                    val lastPaymentDate = doc.getString("lastPaymentDate").orEmpty()
                    val lastVoucherNo = doc.getString("lastVoucherNo").orEmpty()
                    val paidAmount = doc.getDouble("paidAmount") ?: 0.0
                    if (lastPaymentDate.isNotBlank() && paidAmount > 0) {
                        val key = "fee_${doc.id}_${lastVoucherNo}_$lastPaymentDate"
                        if (!prefs.getBoolean(key, false)) {
                            BackgroundNotificationReceiver.showNotification(
                                applicationContext,
                                doc.id.hashCode(),
                                "Fee Payment Received ✔",
                                "Fee payment received on $lastPaymentDate. Voucher: $lastVoucherNo",
                                "Fees"
                            )
                            prefs.edit().putBoolean(key, true).apply()
                        }
                    }
                }

                // Check recent marks / test results
                val marksSnap = runCatching {
                    db.collection("schools").document(schoolId)
                        .collection("marks")
                        .whereEqualTo("studentId", profileId)
                        .limit(5)
                        .get(Source.SERVER)
                        .await()
                }.getOrNull()

                marksSnap?.documents?.forEach { doc ->
                    val testTitle = doc.getString("testTitle") ?: doc.getString("subject").orEmpty()
                    val marksObtained = doc.get("marksObtained")?.toString() ?: ""
                    val key = "mark_${doc.id}_$marksObtained"
                    if (marksObtained.isNotBlank() && !prefs.getBoolean(key, false)) {
                        BackgroundNotificationReceiver.showNotification(
                            applicationContext,
                            doc.id.hashCode(),
                            "Marks Uploaded: $testTitle",
                            "New test result available. Score: $marksObtained",
                            "Results"
                        )
                        prefs.edit().putBoolean(key, true).apply()
                    }
                }

                // Check teacher replies to questions
                val msgSnap = runCatching {
                    db.collection("schools").document(schoolId)
                        .collection("studentTeacherMessages")
                        .whereEqualTo("studentId", profileId)
                        .whereEqualTo("status", "Replied")
                        .limit(5)
                        .get(Source.SERVER)
                        .await()
                }.getOrNull()

                msgSnap?.documents?.forEach { doc ->
                    val teacherName = doc.getString("teacherName") ?: "Teacher"
                    val reply = doc.getString("reply").orEmpty()
                    val key = "msg_reply_${doc.id}"
                    if (reply.isNotBlank() && !prefs.getBoolean(key, false)) {
                        BackgroundNotificationReceiver.showNotification(
                            applicationContext,
                            doc.id.hashCode(),
                            "Sir $teacherName Replied",
                            reply,
                            "AskTeacher"
                        )
                        prefs.edit().putBoolean(key, true).apply()
                    }
                }
            }

            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
