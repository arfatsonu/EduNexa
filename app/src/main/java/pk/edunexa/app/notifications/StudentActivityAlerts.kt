package pk.edunexa.app.notifications

import android.content.Context
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.students.StudentPortalData
import pk.edunexa.app.students.StudentPortalPolicy
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** In-app activity views of existing authorized records; no generated Firestore writes. */
object StudentActivityAlerts {
    private fun hash(vararg parts: String) = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray()).joinToString("") { "%02x".format(it) }
    fun isActivity(item: DiaryItem) = item.id.startsWith("local-activity-") && item.raw["localActivity"] == true
    private fun stamp(row: DiaryItem, vararg fields: String): String? = fields.firstNotNullOfOrNull { key ->
        val value = row.raw[key]
        val date = when(value) {
            is com.google.firebase.Timestamp -> value.toDate()
            is Date -> value
            is String -> NotificationPolicy.millis(value)?.let(::Date) ?: if(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) runCatching { SimpleDateFormat("yyyy-MM-dd",Locale.US).apply { isLenient=false; timeZone=TimeZone.getTimeZone("Asia/Karachi") }.parse(value) }.getOrNull() else null
            else -> null
        }
        date?.let { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US).apply { timeZone=TimeZone.getTimeZone("UTC") }.format(it) }
    }
    fun rows(data: StudentPortalData, schoolId: String): List<DiaryItem> {
        val pupil = data.student
        if(schoolId.isBlank() || pupil.id.isBlank() || pupil.raw["isDeleted"] == true || pupil.text("status") !in setOf("Active","On Leave")) return emptyList()
        val out=mutableListOf<DiaryItem>()
        fun notice(type: String, source: DiaryItem, revision: String, title: String, message: String, time: String?, author: String) {
            if(time == null) return
            val id="local-activity-"+hash(schoolId,pupil.id,type,source.id,revision)
            out += DiaryItem(id,mapOf("id" to id,"schoolId" to schoolId,"localActivity" to true,"activityType" to type,"sourceKind" to when(type) { "attendance" -> "attendance"; "fee" -> "fees"; "homeworkCheck" -> "homeworkFeedback"; else -> "diaryEntries" },"sourceId" to source.id,"title" to title,"message" to message,"recipientIds" to listOf(pupil.id),"recipientId" to pupil.id,"status" to "Published","priority" to if(title == "Marked absent") "Important" else "Normal","audience" to "Individual","publishAt" to time,"createdAt" to time,"expiresAt" to "","createdBy" to author.ifBlank { "School" }))
        }
        fun belongs(row: DiaryItem)=row.text("schoolId").let { it.isBlank() || it == schoolId } && row.text("studentId") == pupil.id
        data.attendance.filter { belongs(it) && data.inSession(it) && it.text("attendanceDate").ifBlank { it.text("date") }.isNotBlank() }
            .groupBy { it.text("attendanceDate").ifBlank { it.text("date") } }.values.forEach { versions ->
                val row=versions.maxWithOrNull(compareBy<DiaryItem>({ stamp(it,"lastUpdatedAt","updatedAt","markedAt","attendanceDate","date").orEmpty() },{it.id}))!!
                if(row.text("status") !in setOf("Present","Absent","Leave","Late")) return@forEach
                notice("attendance",row,row.text("status"),if(row.text("status") == "Absent") "Marked absent" else "Attendance updated",
                    "Your attendance for ${row.text("attendanceDate").ifBlank { row.text("date") }} is ${row.text("status")}.",stamp(row,"lastUpdatedAt","updatedAt","markedAt","attendanceDate","date"),row.text("markedBy"))
            }
        data.fees.filter(::belongs).forEach { row ->
            DiaryPolicy.maps(row.raw["paymentHistory"]).distinctBy { it["id"] }.forEach { payment ->
                val id=payment["id"]?.toString().orEmpty(); val amount=(payment["amount"] as? Number)?.toDouble() ?: 0.0
                if(id.isNotBlank() && amount.isFinite() && amount > 0) {
                    val receipt=payment["receiptNo"]?.toString().orEmpty().ifBlank { payment["receiptVoucherNo"]?.toString().orEmpty() }
                    notice("fee",row,id,"Fee payment received","Payment of Rs. ${StudentPortalPolicy.number(amount)} received${row.text("feeType").takeIf { it.isNotBlank() }?.let { " for $it" }.orEmpty()}.${if(receipt.isNotBlank()) " Receipt: $receipt." else ""}",stamp(DiaryItem(id,payment),"createdAt","paymentDate") ?: stamp(row,"paymentDate","updatedAt"),payment["receivedBy"]?.toString().orEmpty())
                }
            }
        }
        data.currentDiary.filter { it.text("schoolId").let { school -> school.isBlank() || school == schoolId } && pupil.id in (it.raw["studentIds"] as? List<*>).orEmpty() && it.text("homework").isNotBlank() && it.text("status") !in setOf("Draft","Archived","Cancelled") }.forEach { row ->
            val revision=hash(row.text("homework"),row.text("topic"),row.text("dueDate"),row.text("subject"),row.attachment["url"]?.toString().orEmpty())
            notice("homework",row,revision,"Homework assigned", "${row.text("subject").ifBlank { "Homework" }}: ${row.text("homework")}${row.text("dueDate").takeIf { it.isNotBlank() }?.let { "\nDue: $it" }.orEmpty()}",stamp(row,"updatedAt","createdAt","date"),row.text("teacherName"))
        }
        data.feedback.filter { belongs(it) && data.inSession(it) }.forEach { row ->
            val record=StudentPortalPolicy.map(row.raw["record"]);val context=StudentPortalPolicy.map(row.raw["context"])
            val status=record["status"]?.toString().orEmpty()
            if(status in setOf("Completed","Incomplete","Not Done","Absent")) notice("homeworkCheck",row,status,"Homework checked","${context["subject"]?.toString()?.takeIf { it.isNotBlank() } ?: "Homework"}: $status.",stamp(row,"checkedAt"),record["checkedBy"]?.toString().orEmpty())
        }
        return out.distinctBy { it.id }.sortedByDescending { it.text("publishAt") }
    }
    /** Prefer the server notice if Functions are enabled in a future deployment. */
    fun merge(server: List<DiaryItem>, activity: List<DiaryItem>): List<DiaryItem> {
        val covered=server.filter { it.text("sourceKind").isNotBlank() && it.text("sourceId").isNotBlank() }.map { it.text("sourceKind") to it.text("sourceId") }.toSet()
        return (server + activity.filter { (it.text("sourceKind") to it.text("sourceId")) !in covered }).distinctBy { it.id }
    }
}

class StudentActivityReadStore(context: Context, session: PortalSession) {
    val preferences = context.getSharedPreferences("activity-reads-" + listOf(session.uid,session.schoolId,session.profileId.orEmpty()).joinToString("-") { value -> MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) } }, Context.MODE_PRIVATE)
    fun ids(): Set<String> = preferences.getStringSet("read",emptySet()).orEmpty().toSet()
    fun markRead(item: DiaryItem) {
        require(StudentActivityAlerts.isActivity(item))
        preferences.edit().putStringSet("read",ids()+item.id).apply()
    }
}
