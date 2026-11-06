package pk.edunexa.app.notifications

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object NotificationPolicy {
    fun now() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    fun millis(value: String): Long? = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX").firstNotNullOfOrNull { pattern -> runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value)?.time }.getOrNull() }
    val audiences = listOf("All", "Teachers", "Students", "Class", "Individual")
    val priorities = listOf("Normal", "Important", "Urgent")
    fun visible(item: DiaryItem, admin: Boolean, recipient: String, now: Long = System.currentTimeMillis()): Boolean {
        if(admin) return true
        val ids = (item.raw["recipientIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        if(recipient.isBlank() || recipient !in ids || item.text("status") !in setOf("Published", "Scheduled")) return false
        val publish = millis(item.text("publishAt")) ?: return false
        val expiry = item.text("expiresAt").takeIf { it.isNotBlank() }?.let { millis(it) ?: return false }
        return publish <= now && (expiry == null || expiry > now)
    }
    fun recipients(audience: String, teacherRows: List<DiaryItem>, students: List<DiaryItem>, sessionAliases: Set<String>, classId: String, sectionId: String, individual: String): List<String> {
        val teachers = teacherRows.filter { it.text("status") == "Active" }
        val pupils = students.filter { it.text("status") in setOf("Active", "On Leave") && it.text("academicSessionId").ifBlank { it.text("academicSession") } in sessionAliases }
        val allowed = when(audience) {
            "All" -> teachers.map { it.id } + pupils.map { it.id }
            "Teachers" -> teachers.map { it.id }
            "Students" -> pupils.map { it.id }
            "Class" -> {
                require(classId.isNotBlank()) { "Select class." }
                pupils.filter { it.text("classId") == classId && (sectionId.isBlank() || it.text("sectionId") == sectionId) }.map { it.id } + teachers.filter { it.text("inchargeClassId") == classId && (sectionId.isBlank() || it.text("inchargeSectionId") == sectionId) && it.text("inchargeAcademicSession") in sessionAliases }.map { it.id }
            }
            "Individual" -> { require(individual.isNotBlank() && individual in (teachers + pupils).map { it.id }) { "Active recipient required." }; listOf(individual) }
            else -> error("Invalid audience")
        }
        return allowed.distinct()
    }
    fun readId(notificationId: String, recipient: String) = "${notificationId}__${recipient}".replace('/', '_').take(220)
}
data class NotificationDirectory(val config: Map<String, Any?>, val teachers: List<DiaryItem>, val students: List<DiaryItem>) {
    fun options(key: String) = DiaryPolicy.maps(config[key])
    val currentSession get() = options("sessions").firstOrNull { it["isCurrent"] == true }
        ?: options("sessions").firstOrNull { it["status"] == "Active" }
        ?: options("sessions").firstOrNull { it["status"] != "Closed" }
        ?: options("sessions").firstOrNull().orEmpty()
    val aliases get() = setOf(currentSession["id"]?.toString().orEmpty(), currentSession["name"]?.toString().orEmpty()).filter { it.isNotBlank() }.toSet()
}
class NotificationRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val root get() = db.collection("schools").document(actor.schoolId)
    private suspend fun authorize(admin: Boolean = false) {
        check(FirebaseAuth.getInstance().currentUser?.uid == actor.uid) { "Sign in again." }
        val user = db.collection("users").document(actor.uid).get(Source.SERVER).awaitNotice()
        check(user.getBoolean("active") == true && user.getString("schoolId") == actor.schoolId && user.getString("role") == actor.role.name.lowercase()) { "Active school session required." }
        if(admin) check(actor.role == PortalRole.ADMIN) { "Admin only." }
        else if(actor.role != PortalRole.ADMIN) check(user.getString("profileId") == actor.profileId && !actor.profileId.isNullOrBlank())
    }
    suspend fun directory(): NotificationDirectory {
        authorize(true)
        val config = root.collection("academicConfig").document("current").get(Source.SERVER).awaitNotice().data.orEmpty()
        suspend fun rows(name: String) = root.collection(name).get(Source.SERVER).awaitNotice().documents.map { DiaryItem(it.id, it.data.orEmpty()) }
        return NotificationDirectory(config, rows("teachers"), rows("students"))
    }
    suspend fun saveDraft(item: DiaryItem, title: String, message: String, priority: String, audience: String, classId: String, sectionId: String, recipientId: String) {
        authorize(true)
        require(title.trim().isNotBlank() && title.length <= 180 && message.trim().isNotBlank() && message.length <= 10000) { "Title (max 180) and message (max 10,000) required." }
        require(priority in NotificationPolicy.priorities && audience in NotificationPolicy.audiences)
        val directory = directory()
        val sessionId = directory.currentSession["id"]?.toString().orEmpty()
            .ifBlank { directory.options("sessions").firstOrNull()?.get("id")?.toString().orEmpty() }
            .ifBlank { "current-session" }
        val cls = directory.options("classes").firstOrNull { it["id"] == classId && it["isActive"] == true }
        val sec = directory.options("sections").firstOrNull { it["id"] == sectionId && it["classId"] == classId && it["isActive"] == true }
        if(audience == "Class") require(cls != null && (sectionId.isBlank() || sec != null)) { "Select active class/section." }
        val recipients = NotificationPolicy.recipients(audience, directory.teachers, directory.students, directory.aliases, classId, sectionId, recipientId)
        val ref = root.collection("notifications").document(item.id)
        db.runTransaction { tx ->
            val existing = tx.get(ref)
            if(existing.exists()) check(existing.getString("status") == "Draft" && existing.getString("updatedAt").orEmpty() == item.text("updatedAt")) { "Notice changed or already published. Refresh." }
            val now = NotificationPolicy.now()
            tx.set(ref, mapOf("id" to ref.id, "schoolId" to actor.schoolId, "title" to title.trim(), "message" to message.trim(), "priority" to priority, "audience" to audience, "classId" to if(audience == "Class") classId else "", "className" to if(audience == "Class") cls?.get("name").toString() else "", "sectionId" to if(audience == "Class") sectionId else "", "sectionName" to if(audience == "Class") sec?.get("name")?.toString().orEmpty() else "", "recipientId" to if(audience == "Individual") recipientId else "", "recipientName" to (directory.teachers + directory.students).firstOrNull { it.id == recipientId }?.let { it.text("name").ifBlank { it.text("fullName") } }.orEmpty(), "recipientIds" to recipients, "academicSessionId" to sessionId, "status" to "Draft", "publishAt" to "", "expiresAt" to "", "createdBy" to existing.getString("createdBy").orEmpty().ifBlank { actor.name }, "createdAt" to existing.getString("createdAt").orEmpty().ifBlank { now }, "updatedAt" to now, "cloudUpdatedAt" to FieldValue.serverTimestamp()))
        }.awaitNotice()
    }
    suspend fun changeStatus(item: DiaryItem, status: String) {
        authorize(true); require(status in setOf("Published", "Archived"))
        // Re-resolve recipients at publication so drafts do not retain inactive or old-session students.
        val directory = if(status == "Published") directory() else null
        val recipients = directory?.let { NotificationPolicy.recipients(item.text("audience"), it.teachers, it.students, it.aliases, item.text("classId"), item.text("sectionId"), item.text("recipientId")) }
        if(status == "Published") require(!recipients.isNullOrEmpty()) { "No active recipients found for this audience." }
        if(status == "Published") {
            require(recipients!!.toSet() == (item.raw["recipientIds"] as? List<*>)?.filterIsInstance<String>()?.toSet()) { "Recipients changed. Edit/save the draft and review again." }
            if(item.text("audience") == "Class") require(directory!!.options("classes").any { it["id"] == item.text("classId") && it["isActive"] == true } && (item.text("sectionId").isBlank() || directory.options("sections").any { it["id"] == item.text("sectionId") && it["classId"] == item.text("classId") && it["isActive"] == true })) { "Class/section inactive. Edit draft." }
        }
        db.runTransaction { tx ->
            val ref = root.collection("notifications").document(item.id); val old = tx.get(ref)
            check(old.exists() && old.getString("updatedAt").orEmpty() == item.text("updatedAt")) { "Notice changed. Refresh." }
            if(status == "Published") check(old.getString("status") == "Draft") { "Only draft can be published." }
            else check(old.getString("status") != "Archived")
            val update = mutableMapOf<String, Any>("status" to status, "updatedAt" to NotificationPolicy.now(), "cloudUpdatedAt" to FieldValue.serverTimestamp())
            if(status == "Published") { update["recipientIds"] = recipients!!; update["publishAt"] = NotificationPolicy.now() }
            tx.update(ref, update)
        }.awaitNotice()
    }
    suspend fun markRead(item: DiaryItem) {
        authorize(); check(actor.role != PortalRole.ADMIN)
        val recipient = actor.profileId!!
        val notice = root.collection("notifications").document(item.id).get(Source.SERVER).awaitNotice()
        check(notice.exists() && NotificationPolicy.visible(DiaryItem(notice.id, notice.data.orEmpty()), false, recipient)) { "Notice no longer available." }
        root.collection("notificationReads").document(NotificationPolicy.readId(item.id, recipient)).set(mapOf("schoolId" to actor.schoolId, "notificationId" to item.id, "userId" to recipient, "readAt" to NotificationPolicy.now(), "cloudUpdatedAt" to FieldValue.serverTimestamp())).awaitNotice()
    }
}
private suspend fun <T> Task<T>.awaitNotice(): T = suspendCancellableCoroutine { c -> addOnSuccessListener { if(c.isActive) c.resume(it) }; addOnFailureListener { if(c.isActive) c.resumeWithException(it) } }
