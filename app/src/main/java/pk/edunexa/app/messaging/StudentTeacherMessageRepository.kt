package pk.edunexa.app.messaging

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StudentTeacherMessage(
    val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val rollNo: String = "",
    val classId: String = "",
    val className: String = "",
    val sectionId: String = "",
    val section: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val question: String = "",
    val reply: String = "",
    val status: String = "Pending", // Pending, Replied
    val createdAt: Long = System.currentTimeMillis(),
    val repliedAt: Long = 0L
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "schoolId" to schoolId,
        "studentId" to studentId,
        "studentName" to studentName,
        "rollNo" to rollNo,
        "classId" to classId,
        "className" to className,
        "sectionId" to sectionId,
        "section" to section,
        "subjectId" to subjectId,
        "subjectName" to subjectName,
        "teacherId" to teacherId,
        "teacherName" to teacherName,
        "question" to question,
        "reply" to reply,
        "status" to status,
        "createdAt" to createdAt,
        "repliedAt" to repliedAt
    )

    fun toJson(): JSONObject = JSONObject(toMap())

    companion object {
        fun fromDoc(id: String, d: Map<String, Any?>): StudentTeacherMessage = StudentTeacherMessage(
            id = id,
            schoolId = d["schoolId"]?.toString().orEmpty(),
            studentId = d["studentId"]?.toString().orEmpty(),
            studentName = d["studentName"]?.toString().orEmpty(),
            rollNo = d["rollNo"]?.toString().orEmpty(),
            classId = d["classId"]?.toString().orEmpty(),
            className = d["className"]?.toString().orEmpty(),
            sectionId = d["sectionId"]?.toString().orEmpty(),
            section = d["section"]?.toString().orEmpty(),
            subjectId = d["subjectId"]?.toString().orEmpty(),
            subjectName = d["subjectName"]?.toString().orEmpty(),
            teacherId = d["teacherId"]?.toString().orEmpty(),
            teacherName = d["teacherName"]?.toString().orEmpty(),
            question = d["question"]?.toString().orEmpty(),
            reply = d["reply"]?.toString().orEmpty(),
            status = d["status"]?.toString() ?: "Pending",
            createdAt = (d["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            repliedAt = (d["repliedAt"] as? Number)?.toLong() ?: 0L
        )

        fun fromJson(json: JSONObject): StudentTeacherMessage = StudentTeacherMessage(
            id = json.optString("id"),
            schoolId = json.optString("schoolId"),
            studentId = json.optString("studentId"),
            studentName = json.optString("studentName"),
            rollNo = json.optString("rollNo"),
            classId = json.optString("classId"),
            className = json.optString("className"),
            sectionId = json.optString("sectionId"),
            section = json.optString("section"),
            subjectId = json.optString("subjectId"),
            subjectName = json.optString("subjectName"),
            teacherId = json.optString("teacherId"),
            teacherName = json.optString("teacherName"),
            question = json.optString("question"),
            reply = json.optString("reply"),
            status = json.optString("status", "Pending"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            repliedAt = json.optLong("repliedAt", 0L)
        )
    }
}

class StudentTeacherMessageRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun col(schoolId: String) = db.collection("schools").document(schoolId).collection("studentTeacherMessages")

    private fun getPrefs(): android.content.SharedPreferences? = runCatching {
        val ctx = FirebaseApp.getInstance().applicationContext
        ctx.getSharedPreferences("edunexa_local_questions", Context.MODE_PRIVATE)
    }.getOrNull()

    private fun loadLocal(studentId: String): List<StudentTeacherMessage> {
        val prefs = getPrefs() ?: return emptyList()
        val str = prefs.getString("questions_$studentId", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(str)
            val list = mutableListOf<StudentTeacherMessage>()
            for (i in 0 until arr.length()) {
                list.add(StudentTeacherMessage.fromJson(arr.getJSONObject(i)))
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun saveLocal(msg: StudentTeacherMessage) {
        val prefs = getPrefs() ?: return
        val current = loadLocal(msg.studentId).filterNot { it.id == msg.id }
        val updated = current + msg
        val arr = JSONArray()
        updated.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("questions_${msg.studentId}", arr.toString()).apply()
    }

    suspend fun loadForStudent(schoolId: String, studentId: String): List<StudentTeacherMessage> = withContext(Dispatchers.IO) {
        val remote = runCatching {
            val snap = col(schoolId)
                .whereEqualTo("studentId", studentId)
                .get(Source.DEFAULT)
                .await()
            snap.documents.map { StudentTeacherMessage.fromDoc(it.id, it.data.orEmpty()) }
        }.getOrDefault(emptyList())

        val local = loadLocal(studentId)
        (remote + local).distinctBy { it.id }.sortedByDescending { it.createdAt }
    }

    suspend fun loadForTeacher(schoolId: String, teacherId: String): List<StudentTeacherMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val snap = col(schoolId)
                .whereEqualTo("teacherId", teacherId)
                .get(Source.DEFAULT)
                .await()
            snap.documents.map { StudentTeacherMessage.fromDoc(it.id, it.data.orEmpty()) }
                .sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    suspend fun sendQuestion(msg: StudentTeacherMessage): String = withContext(Dispatchers.IO) {
        val id = msg.id.ifBlank { "msg-" + UUID.randomUUID().toString() }
        val toSave = msg.copy(id = id, createdAt = System.currentTimeMillis(), status = "Pending")
        saveLocal(toSave)
        runCatching {
            col(msg.schoolId).document(id).set(toSave.toMap()).await()
        }
        id
    }

    suspend fun sendReply(schoolId: String, messageId: String, replyText: String) = withContext(Dispatchers.IO) {
        col(schoolId).document(messageId).update(
            mapOf(
                "reply" to replyText.trim(),
                "status" to "Replied",
                "repliedAt" to System.currentTimeMillis()
            )
        ).await()
    }
}
