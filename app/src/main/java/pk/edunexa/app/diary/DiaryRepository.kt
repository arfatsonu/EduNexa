package pk.edunexa.app.diary

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import pk.edunexa.app.academic.AcademicPolicy
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class DiaryItem(val id: String = "", val raw: Map<String, Any?> = emptyMap()) {
    fun text(key: String) = raw[key]?.toString().orEmpty()
    val attachment: Map<String, Any?> get() = (raw["attachment"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
}
data class DiaryData(val config: Map<String, Any?>, val entries: List<DiaryItem>, val checks: List<DiaryItem>, val students: List<DiaryItem>) {
    fun options(key: String) = DiaryPolicy.maps(config[key])
    val currentSession get() = pk.edunexa.app.academic.AcademicSessionPolicy.current(config)
    fun roster(entry: DiaryItem) = students.filter { student ->
        student.text("classId") == entry.text("classId") && student.text("sectionId") == entry.text("sectionId") &&
            student.text("status") in setOf("Active", "On Leave") &&
            student.text("academicSessionId").ifBlank { student.text("academicSession") }.let { it == entry.text("academicSessionId") || it == entry.text("academicSession") }
    }
}
data class PendingDiaryFile(val name: String, val mime: String, val bytes: ByteArray)

object DiaryPolicy {
    val statuses = listOf("Completed", "Incomplete", "Not Done", "Absent", "Not Checked")
    fun maps(value: Any?): List<Map<String, Any?>> = (value as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value } }.orEmpty()
    fun key(vararg parts: String): String = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray()).joinToString("") { "%02x".format(it) }
    fun validate(date: String, due: String, topic: String) {
        require(date.isNotBlank() && due.isNotBlank()) { "Diary and due dates are required." }
        AcademicPolicy.validateDates(date, due)
        require(topic.trim().isNotEmpty()) { "Topic is required." }
    }
    fun validateFile(file: PendingDiaryFile) {
        require(file.bytes.isNotEmpty() && file.bytes.size < 2 * 1024 * 1024) { "The attachment must be smaller than 2 MB." }
        require(file.mime == "application/pdf" || file.mime.startsWith("image/")) { "Select a PDF or image file." }
    }
}

class DiaryRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun root(school: String) = db.collection("schools").document(school)
    suspend fun load(school: String): DiaryData {
        val root = root(school)
        val configTask = root.collection("academicConfig").document("current").get(Source.SERVER)
        val diaryTask = root.collection("diaryEntries").get(Source.SERVER)
        val homeworkTask = root.collection("homeworkSessions").get(Source.SERVER)
        val studentsTask = root.collection("students").get(Source.SERVER)
        val config = configTask.ready()
        check(config.exists()) { "Academic settings are unavailable." }
        fun items(docs: List<com.google.firebase.firestore.DocumentSnapshot>) = docs.map { DiaryItem(it.id, it.data.orEmpty()) }
        return DiaryData(config.data.orEmpty(), items(diaryTask.ready().documents).sortedByDescending { it.text("date") },
            items(homeworkTask.ready().documents), items(studentsTask.ready().documents))
    }
    private suspend fun <T> Task<T>.ready(): T = suspendCoroutine { c -> addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }; addOnCanceledListener { c.resumeWithException(IllegalStateException("Operation cancelled.")) } }
}


