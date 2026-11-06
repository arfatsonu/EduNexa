package pk.edunexa.app.reports

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ReportRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    suspend fun load(category: String): ReportData {
        require(category in ReportPolicy.categories)
        check(actor.role == PortalRole.ADMIN && FirebaseAuth.getInstance().currentUser?.uid == actor.uid) { "Active Admin login required." }
        val user = db.collection("users").document(actor.uid).get(Source.SERVER).reportAwait()
        check(user.getBoolean("active") == true && user.getString("role") == "admin" && user.getString("schoolId") == actor.schoolId) { "Admin access required." }
        val root = db.collection("schools").document(actor.schoolId)
        val config = root.collection("academicConfig").document("current").get(Source.SERVER).reportAwait().data.orEmpty()
        suspend fun read(collection: String) = root.collection(collection).get(Source.SERVER).reportAwait().documents.map { DiaryItem(it.id, it.data.orEmpty()) }
        val collection = when(category) { "Students" -> "students"; "Attendance" -> "attendance"; "Fees" -> "fees"; "Exams" -> "tests"; else -> "teachers" }
        val records = read(collection).filter { collection !in setOf("students", "teachers") || it.raw["isDeleted"] != true }
        val related = when(category) { "Students" -> read("fees"); "Exams" -> read("marks"); else -> emptyList() }
        val settings = root.get(Source.SERVER).reportAwait().get("settings") as? Map<*, *>
        return ReportData(config, records, related, settings?.get("schoolName")?.toString().orEmpty().ifBlank { "EduNexa" }, settings?.entries?.associate { it.key.toString() to it.value }.orEmpty())
    }
}
private suspend fun <T> Task<T>.reportAwait(): T = suspendCancellableCoroutine { continuation -> addOnSuccessListener { if(continuation.isActive) continuation.resume(it) }; addOnFailureListener { if(continuation.isActive) continuation.resumeWithException(it) } }

