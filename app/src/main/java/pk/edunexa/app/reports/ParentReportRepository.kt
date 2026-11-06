package pk.edunexa.app.reports

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ParentReportRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    suspend fun load(studentId: String): ParentReportData {
        require(studentId.isNotBlank())
        check(actor.role == PortalRole.ADMIN && FirebaseAuth.getInstance().currentUser?.uid == actor.uid) { "Active Admin login required." }
        val user = db.collection("users").document(actor.uid).get(Source.SERVER).awaitParent()
        check(user.getString("role") == "admin" && user.getBoolean("active") == true && user.getString("schoolId") == actor.schoolId) { "Admin access required." }
        val root = db.collection("schools").document(actor.schoolId)
        val student = root.collection("students").document(studentId).get(Source.SERVER)
        val config = root.collection("academicConfig").document("current").get(Source.SERVER)
        val school = root.get(Source.SERVER)
        val attendance = root.collection("attendance").whereEqualTo("studentId",studentId).get(Source.SERVER)
        val marks = root.collection("marks").whereEqualTo("studentId",studentId).get(Source.SERVER)
        val diaries = root.collection("diaryEntries").get(Source.SERVER)
        val checks = root.collection("homeworkSessions").get(Source.SERVER)
        val tests = root.collection("tests").get(Source.SERVER)
        val fees=root.collection("fees").whereEqualTo("studentId",studentId).get(Source.SERVER)
        val tasks = listOf(student,config,school,attendance,marks,diaries,checks,tests,fees)
        Tasks.whenAllComplete(tasks).awaitParent()
        tasks.forEach { if(!it.isSuccessful) throw it.exception ?: IllegalStateException("Report load was cancelled.") }
        check(student.result.exists()) { "The student record was not found." }
        fun rows(value: com.google.firebase.firestore.QuerySnapshot) = value.documents.map { DiaryItem(it.id,it.data.orEmpty()) }
        val settings = (school.result.get("settings") as? Map<*,*>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
        return ParentReportData(DiaryItem(studentId,student.result.data.orEmpty()),config.result.data.orEmpty(),settings,rows(attendance.result),rows(diaries.result),rows(checks.result),rows(tests.result),rows(marks.result),rows(fees.result))
    }
}
private suspend fun <T> Task<T>.awaitParent(): T = suspendCancellableCoroutine { c -> addOnSuccessListener { if(c.isActive)c.resume(it) };addOnFailureListener { if(c.isActive)c.resumeWithException(it) };addOnCanceledListener { c.cancel() } }
