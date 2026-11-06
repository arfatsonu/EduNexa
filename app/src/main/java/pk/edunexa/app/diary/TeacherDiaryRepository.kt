package pk.edunexa.app.diary

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import pk.edunexa.app.auth.PortalSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object TeacherDiaryPolicy {
    fun canWrite(config: Map<String, Any?>, entry: DiaryItem, teacherId: String): Boolean {
        val current = pk.edunexa.app.academic.AcademicSessionPolicy.current(config)["id"]
        return current != null && current == entry.text("academicSessionId") && DiaryPolicy.maps(config["teacherAssignments"]).any {
            it["teacherId"] == teacherId && it["academicSession"] == current && it["classId"] == entry.text("classId") && it["sectionId"] == entry.text("sectionId") && it["subjectId"] == entry.text("subjectId")
        }
    }
}

class TeacherDiaryRepository(private val session: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val teacherId = session.profileId ?: error("Teacher profile missing.")
    private val root get() = db.collection("schools").document(session.schoolId)
    private suspend fun profile(): Map<String, Any?> {
        check(FirebaseAuth.getInstance().currentUser?.uid == session.uid) { "Sign in again." }
        val user = db.collection("users").document(session.uid).get(Source.SERVER).ready()
        check(user.getString("role") == "teacher" && user.getBoolean("active") == true && user.getBoolean("portalDisabled") != true && user.getString("schoolId") == session.schoolId && user.getString("profileId") == teacherId) { "Active teacher login required." }
        val teacher = root.collection("teachers").document(teacherId).get(Source.SERVER).ready()
        check(teacher.getString("status") == "Active") { "This teacher is inactive." }
        return teacher.data.orEmpty()
    }
    private fun config(profile: Map<String, Any?>): Map<String, Any?> = (profile["academicDirectory"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
    suspend fun load(school: String): DiaryData {
        check(school == session.schoolId)
        val teacher = profile(); val config = config(teacher)
        check(config.isNotEmpty()) { "Ask Admin to save teacher assignments to sync academic access." }
        val diaryTask = root.collection("diaryEntries").whereEqualTo("teacherId", teacherId).get(Source.SERVER)
        val homeworkTask = root.collection("homeworkSessions").whereEqualTo("checkedByTeacherId", teacherId).get(Source.SERVER)
        val currentId = pk.edunexa.app.academic.AcademicSessionPolicy.current(config)["id"]
        val inchargeSession = teacher["inchargeAcademicSession"]?.toString().orEmpty()
        val cls = teacher["inchargeClassId"]?.toString().orEmpty(); val section = teacher["inchargeSectionId"]?.toString().orEmpty()
        val incharge = cls.isNotBlank() && section.isNotBlank() && (inchargeSession.isBlank() || inchargeSession == currentId)
        val classDiaryTask = if (incharge) root.collection("diaryEntries").whereEqualTo("classId", cls).whereEqualTo("sectionId", section).get(Source.SERVER) else null
        val classHomeworkTask = if (incharge) root.collection("homeworkSessions").whereEqualTo("classId", cls).whereEqualTo("sectionId", section).get(Source.SERVER) else null
        val diary = diaryTask.ready().documents.toMutableList()
        val homework = homeworkTask.ready().documents.toMutableList()
        if (incharge) {
            diary += classDiaryTask!!.ready().documents
            homework += classHomeworkTask!!.ready().documents
        }
        return DiaryData(config, diary.distinctBy { it.id }.map { DiaryItem(it.id, it.data.orEmpty()) }.sortedByDescending { it.text("date") },
            homework.distinctBy { it.id }.map { DiaryItem(it.id, it.data.orEmpty()) }, DiaryPolicy.maps(config["studentRoster"]).map { DiaryItem(it["id"].toString(), it) })
    }
    private suspend fun writable(entry: DiaryItem): Map<String, Any?> {
        val teacher = profile(); val config = config(teacher)
        check(TeacherDiaryPolicy.canWrite(config, entry, teacherId)) { "You can only manage your assigned classes, sections and subjects in the current session." }
        check(entry.id.isBlank() || entry.text("teacherId") == teacherId) { "Another teacher's diary can only be viewed." }
        return config
    }
    suspend fun save(school: String, draft: DiaryItem, file: PendingDiaryFile?, teacherName: String) {
        check(school == session.schoolId)
        val config = writable(draft)
        DiaryPolicy.validate(draft.text("date"), draft.text("dueDate"), draft.text("topic")); file?.let { DiaryPolicy.validateFile(it) }
        val isNew = draft.id.isBlank()
        val id = draft.id.ifBlank { "diary-" + DiaryPolicy.key(draft.text("academicSessionId"), draft.text("date"), draft.text("classId"), draft.text("sectionId"), draft.text("subjectId")) }
        val own = root.collection("diaryEntries").whereEqualTo("teacherId", teacherId).get(Source.SERVER).ready().documents
        check(own.none { it.id != draft.id && it.getString("academicSessionId") == draft.text("academicSessionId") && it.getString("date") == draft.text("date") && it.getString("classId") == draft.text("classId") && it.getString("sectionId") == draft.text("sectionId") && it.getString("subjectId") == draft.text("subjectId") }) { "This diary already exists. Edit the existing entry." }
        val old = own.firstOrNull { it.id == draft.id }
        check(isNew || old != null) { "The diary was not found. Refresh and try again." }
        if (!isNew) {
            check(old!!.getString("updatedAt").orEmpty() == draft.text("updatedAt")) { "The diary has changed. Refresh before editing again." }
            listOf("academicSessionId", "date", "classId", "sectionId", "subjectId").forEach { check(old.getString(it).orEmpty() == draft.text(it)) { "The scope of an existing diary cannot change." } }
        }
        var uploaded: String? = null
        var attachment = draft.attachment
        try {
            if (file != null) {
                val path = "schools/$school/diary/$id/${UUID.randomUUID()}-${file.name.replace(Regex("[^a-zA-Z0-9._-]"), "-").take(100)}"
                try {
                    val ref = FirebaseStorage.getInstance().reference.child(path)
                    val uploadTask = ref.putBytes(file.bytes, StorageMetadata.Builder().setContentType(file.mime).build())
                    uploadTask.ready()
                    val downloadUrl = ref.downloadUrl.ready().toString()
                    uploaded = path
                    attachment = mapOf("name" to file.name, "type" to if (file.mime == "application/pdf") "pdf" else "image", "size" to "${file.bytes.size / 1024} KB", "url" to downloadUrl, "storagePath" to path)
                } catch (storageEx: Exception) {
                    if (file.mime.startsWith("image/") && file.bytes.size <= 500 * 1024) {
                        val base64Data = "data:${file.mime};base64," + android.util.Base64.encodeToString(file.bytes, android.util.Base64.NO_WRAP)
                        attachment = mapOf("name" to file.name, "type" to "image", "size" to "${file.bytes.size / 1024} KB", "url" to base64Data, "storagePath" to "")
                    } else {
                        val msg = storageEx.message.orEmpty()
                        val friendly = if (msg.contains("Object does not exist", true) || msg.contains("-13010", true)) {
                            "Firebase Storage is not enabled yet on edunexa-portal. Please enable Storage in Firebase Console, or attach an image under 500 KB."
                        } else storageEx.message ?: "Could not upload file to storage."
                        throw IllegalStateException(friendly, storageEx)
                    }
                }
            }
            val now = timestamp()
            fun name(type: String, key: String) = DiaryPolicy.maps(config[type]).firstOrNull { it["id"] == draft.text(key) }?.get("name")?.toString().orEmpty()
            val roster = pk.edunexa.app.teachers.TeacherPortalPolicy.roster(pk.edunexa.app.teachers.TeacherAccess(mapOf("id" to teacherId),config),draft.text("classId"),draft.text("sectionId")).map { it.raw }
            val payload = draft.raw + mapOf("id" to id, "schoolId" to school, "className" to name("classes", "classId"), "section" to name("sections", "sectionId"), "subject" to name("subjects", "subjectId"), "academicSession" to name("sessions", "academicSessionId"), "teacherId" to teacherId, "teacherName" to session.name,
                "studentIds" to (old?.get("studentIds") ?: roster.map { it["id"] }), "accessKey" to "${draft.text("classId")}__${draft.text("sectionId")}__${draft.text("subjectId")}", "attachment" to attachment.ifEmpty { null }, "createdBy" to (old?.getString("createdBy") ?: session.name), "createdAt" to (old?.getString("createdAt") ?: now), "updatedAt" to now)
            val ref = root.collection("diaryEntries").document(id)
            db.runTransaction { tx ->
                val latest = tx.get(ref)
                if(isNew) check(!latest.exists()) { "This diary already exists. Refresh and edit it." }
                else check(latest.getString("updatedAt").orEmpty() == draft.text("updatedAt")) { "The diary has changed. Refresh and try again." }
                tx.set(ref, payload, SetOptions.merge())
            }.ready()
        } catch (e: Exception) { uploaded?.let { runCatching { FirebaseStorage.getInstance().reference.child(it).delete().ready() } }; throw e }
        val oldPath = (old?.get("attachment") as? Map<*, *>)?.get("storagePath") as? String
        if (oldPath != null && oldPath != attachment["storagePath"]) runCatching { FirebaseStorage.getInstance().reference.child(oldPath).delete().ready() }
    }
    suspend fun delete(school: String, entry: DiaryItem) {
        check(school == session.schoolId); writable(entry)
        val checks = root.collection("homeworkSessions").whereEqualTo("checkedByTeacherId", teacherId).get(Source.SERVER).ready()
        check(checks.documents.none { it.getString("diaryId") == entry.id }) { "Saved homework checks are linked. Edit the diary instead." }
        val ref = root.collection("diaryEntries").document(entry.id)
        db.runTransaction { tx ->
            val latest = tx.get(ref)
            check(latest.getString("updatedAt").orEmpty() == entry.text("updatedAt")) { "The diary has changed. Refresh and try again." }
            check(latest.getString("homeworkCheckId").isNullOrBlank()) { "Saved homework checks are linked." }
            tx.delete(ref)
        }.ready()
        (entry.attachment["storagePath"] as? String)?.let { runCatching { FirebaseStorage.getInstance().reference.child(it).delete().ready() } }
    }
    suspend fun checkHomework(school: String, entry: DiaryItem, records: List<Map<String, Any?>>, previous: DiaryItem?, teacherName: String) {
        check(school == session.schoolId); val config = writable(entry)
        require(records.isNotEmpty() && records.size < 400 && records.all { it["status"] in DiaryPolicy.statuses }) { "Invalid homework records." }
        val allowed = DiaryPolicy.maps(config["studentRoster"]).filter { it["classId"] == entry.text("classId") && it["sectionId"] == entry.text("sectionId") }.map { it["id"] }.toSet() + DiaryPolicy.maps(previous?.raw?.get("records")).map { it["studentId"] }
        require(records.all { it["studentId"] in allowed } && records.map { it["studentId"] }.distinct().size == records.size) { "These students do not belong to this class." }
        require(previous != null || entry.text("homeworkCheckId").isBlank()) { "Homework checks already exist. Refresh and try again." }
        val id = previous?.id ?: "hw-" + DiaryPolicy.key(entry.id)
        val now = timestamp()
        val payload = entry.raw.filterKeys { it in setOf("academicSessionId", "academicSession", "classId", "className", "sectionId", "section", "subjectId", "subject", "date", "accessKey") } + mapOf("id" to id, "schoolId" to school, "diaryId" to entry.id, "checkedBy" to session.name, "checkedByTeacherId" to teacherId, "checkedAt" to now, "studentIds" to records.map { it["studentId"] }, "records" to records.map { it + mapOf("checkedBy" to session.name, "checkedAt" to now) })
        val ref = root.collection("homeworkSessions").document(id)
        db.runTransaction { tx ->
            val diaryRef = root.collection("diaryEntries").document(entry.id)
            val current = tx.get(diaryRef)
            val old = tx.get(ref)
            check(current.exists() && current.getString("teacherId") == teacherId && current.getString("updatedAt").orEmpty() == entry.text("updatedAt")) { "The diary changed. Refresh homework before saving." }
            if(previous == null) {
                check(!old.exists() && current.getString("homeworkCheckId").isNullOrBlank()) { "Homework checks already exist. Refresh and try again." }
            } else {
                check(old.exists() && old.getString("diaryId") == entry.id && old.getString("checkedByTeacherId") == teacherId && old.getString("checkedAt").orEmpty() == previous.text("checkedAt")) { "Homework checks have changed. Refresh and try again." }
            }
            tx.set(ref,payload,SetOptions.merge())
            DiaryPolicy.maps(payload["records"]).forEachIndexed { index, row ->
                val studentId = row["studentId"].toString()
                tx.set(root.collection("homeworkFeedback").document(pk.edunexa.app.students.StudentPortalPolicy.feedbackId(id,studentId)),pk.edunexa.app.students.StudentPortalPolicy.feedbackPayload(payload,id,index))
            }
            tx.update(diaryRef,"homeworkCheckId",id)
        }.ready()
    }
    private fun timestamp() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
    private suspend fun <T> Task<T>.ready(): T = suspendCoroutine { c -> addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }; addOnCanceledListener { c.resumeWithException(IllegalStateException("Operation cancelled.")) } }
}


