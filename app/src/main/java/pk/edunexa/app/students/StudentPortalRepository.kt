package pk.edunexa.app.students

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.fees.FeePolicy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class StudentPortalData(
    val student: DiaryItem, val schoolName: String,
    val attendance: List<DiaryItem>, val diary: List<DiaryItem>,
    val feedback: List<DiaryItem>, val tests: List<DiaryItem>,
    val results: List<DiaryItem>, val fees: List<DiaryItem>, val datesheets: List<DiaryItem>
) {
    private val savedSession get() = student.text("academicSessionId").ifBlank { student.text("academicSession") }
    private val sessionNames get() = (diary + tests + fees + datesheets).filter { savedSession.isNotBlank() && it.text("academicSessionId") == savedSession }.map { it.text("academicSession") }.filter { it.isNotBlank() && it != savedSession && StudentPortalPolicy.readableSession(it) }.toSet()
    val sessionAliases get() = (setOf(student.text("academicSessionId"), student.text("academicSession")) + sessionNames).filter { it.isNotBlank() }.toSet()
    val sessionLabel get() = listOf(student.text("academicSessionName"), sessionNames.firstOrNull().orEmpty(), student.text("academicSession"))
        .firstOrNull { StudentPortalPolicy.readableSession(it) }
        ?: if(savedSession.isBlank()) "All recorded sessions" else "Academic session"
    fun inSession(row: DiaryItem) = sessionAliases.isEmpty() || row.text("academicSessionId").ifBlank { row.text("academicSession") } in sessionAliases
    val currentDiary get() = diary.filter { inSession(it) && StudentPortalPolicy.classMatches(it, student) }
    val currentTests get() = tests.filter { inSession(it) && StudentPortalPolicy.classMatches(it, student) }
    val currentResults get() = if (results.any { inSession(it) }) results.filter { inSession(it) } else results
    fun check(entry: DiaryItem) = feedback.filter { it.text("diaryId") == entry.id && it.text("studentId") == student.id }.maxByOrNull { it.text("checkedAt") }
    fun homeworkStatus(entry: DiaryItem) = check(entry)?.let { StudentPortalPolicy.map(it.raw["record"])["status"]?.toString() }?.takeIf { it in DiaryPolicy.statuses } ?: "Not Checked"
    val pendingHomework get() = currentDiary.count { it.text("homework").isNotBlank() && homeworkStatus(it) in setOf("Not Checked", "Incomplete", "Not Done", "Absent") }
    val tuitionSnapshot get() = FeePolicy.displaySnapshot(fees, student.id, sessionAliases)
}

object StudentPortalPolicy {
    fun readableSession(value: String): Boolean = value.isNotBlank() &&
        !value.matches(Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) &&
        !value.matches(Regex("(?i)[0-9a-f]{20,}"))

    fun map(value: Any?): Map<String, Any?> = (value as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
    fun classMatches(row: DiaryItem, student: DiaryItem) = row.text("classId") == student.text("classId") && row.text("sectionId") == student.text("sectionId")
    fun attendance(rows: List<DiaryItem>, month: String): List<DiaryItem> = rows.filter {
        it.text("attendanceDate").startsWith(month + "-")
    }.groupBy { it.text("attendanceDate") }.values.map { duplicates ->
        duplicates.maxWithOrNull(compareBy<DiaryItem>({ it.text("lastUpdatedAt").ifBlank { it.text("updatedAt").ifBlank { it.text("markedAt") } } }, { it.id }))!!
    }.filter { it.text("status") in setOf("Present", "Absent", "Leave", "Late") }.sortedByDescending { it.text("attendanceDate") }
    fun attendancePercent(rows: List<DiaryItem>): String {
        if(rows.isEmpty()) return "—"
        val attended = rows.count { it.text("status") in setOf("Present", "Late") }
        return "${kotlin.math.round(attended * 100.0 / rows.size).toInt()}%"
    }
    fun feedbackId(homework: String, student: String) = ("${homework}__${student}").replace('/', '_').take(220)
    fun feedbackPayload(source: Map<String, Any?>, homeworkId: String, index: Int): Map<String, Any?> {
        val row = DiaryPolicy.maps(source["records"])[index]
        return mapOf("schoolId" to source["schoolId"], "studentId" to row["studentId"], "homeworkSessionId" to homeworkId, "diaryId" to source["diaryId"], "academicSessionId" to source["academicSessionId"], "checkedAt" to source["checkedAt"], "recordIndex" to index, "record" to row, "context" to listOf("academicSession","classId","className","sectionId","section","subjectId","subject","date").associateWith { source[it]?.toString().orEmpty() })
    }
    fun resultScore(row: DiaryItem): String {
        if(row.text("status") == "Absent") return "Absent"
        if(row.text("status") == "Exempt") return "Exempt"
        val score = (row.raw["marksObtained"] as? Number)?.toDouble() ?: return "Not entered"
        val total = (row.raw["totalMarks"] as? Number)?.toDouble()
        return number(score) + if(total != null && total > 0) " / " + number(total) else ""
    }
    fun number(value: Double) = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(value)
}

class StudentPortalRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val root get() = db.collection("schools").document(actor.schoolId)
    suspend fun load(): StudentPortalData = coroutineScope {
        check(actor.role == PortalRole.STUDENT && !actor.profileId.isNullOrBlank() && FirebaseAuth.getInstance().currentUser?.uid == actor.uid) { "Sign in with your student account." }
        val user = db.collection("users").document(actor.uid).get(Source.SERVER).awaitStudent()
        check(user.getBoolean("active") == true && user.getBoolean("portalDisabled") != true && user.getString("role") == "student" && user.getString("schoolId") == actor.schoolId && user.getString("profileId") == actor.profileId) { "Student access changed. Sign in again or contact Admin." }
        val profile = root.collection("students").document(actor.profileId!!).get(Source.SERVER).awaitStudent()
        check(profile.exists() && profile.getBoolean("isDeleted") != true && profile.getString("status") in setOf("Active", "On Leave")) { "This student portal is inactive. Contact Admin." }
        val student = DiaryItem(profile.id, profile.data.orEmpty())
        suspend fun rows(query: Query): List<DiaryItem> = runCatching {
            query.get(Source.SERVER).awaitStudent().documents.map { DiaryItem(it.id, it.data.orEmpty()) }
        }.getOrElse {
            runCatching {
                query.get(Source.DEFAULT).awaitStudent().documents.map { DiaryItem(it.id, it.data.orEmpty()) }
            }.getOrDefault(emptyList())
        }
        fun own(name: String) = root.collection(name).whereEqualTo("studentId", student.id)
        val attendance = async { rows(own("attendance")) }
        val fees = async { rows(own("fees")) }
        val feedback = async { rows(own("homeworkFeedback")) }
        val diary = async { rows(root.collection("diaryEntries").whereArrayContains("studentIds", student.id)) }
        val tests = async { rows(root.collection("tests").whereArrayContains("studentIds", student.id)) }
        val results = async {
            val pub = rows(own("publishedResults"))
            val marksRows = rows(own("marks"))
            val tList = tests.await()
            val direct = marksRows.map { m ->
                val matching = tList.firstOrNull { it.id == m.text("testId") }
                DiaryItem(
                    id = m.id,
                    raw = m.raw + mapOf(
                        "testTitle" to matching?.text("title").orEmpty().ifBlank { m.text("testTitle").ifBlank { "Test" } },
                        "subject" to matching?.text("subject").orEmpty().ifBlank { m.text("subject") },
                        "date" to matching?.text("date").orEmpty().ifBlank { m.text("date") },
                        "totalMarks" to (matching?.raw?.get("totalMarks") ?: m.raw["totalMarks"]),
                        "assessmentType" to matching?.text("assessmentType").orEmpty().ifBlank { m.text("assessmentType") },
                        "marksObtained" to m.raw["marksObtained"],
                        "status" to m.text("status")
                    )
                )
            }
            (pub + direct).distinctBy { it.text("testId").ifBlank { it.id } }
        }
        val sheets = async { rows(root.collection("examSessions").whereEqualTo("targetClassId", student.text("classId"))) }
        val school = root.get(Source.SERVER).awaitStudent()
        val name = StudentPortalPolicy.map(school.get("settings"))["schoolName"]?.toString().orEmpty().ifBlank { "EduNexa" }
        StudentPortalData(student,name,attendance.await(),diary.await().sortedByDescending { it.text("date") },feedback.await(),tests.await().sortedBy { it.text("date") },results.await().sortedByDescending { it.text("date").ifBlank { it.text("publishedAt") } },fees.await().map { DiaryItem(it.id, FeePolicy.normalize(it.raw)) },sheets.await().filter { it.text("kind") == "termDatesheet" && it.text("targetSectionId") in setOf("",student.text("sectionId")) && it.text("status") !in setOf("Cancelled","Draft","Archived") }.sortedByDescending { it.text("startDate") })
    }
    fun observe(student: DiaryItem, changed: () -> Unit, failed: (String) -> Unit): List<ListenerRegistration> {
        val id = student.id
        val sources = listOf(
            root.collection("students").whereEqualTo(FieldPath.documentId(),id),
            root.collection("attendance").whereEqualTo("studentId",id),
            root.collection("fees").whereEqualTo("studentId",id),
            root.collection("homeworkFeedback").whereEqualTo("studentId",id),
            root.collection("publishedResults").whereEqualTo("studentId",id),
            root.collection("diaryEntries").whereArrayContains("studentIds",id),
            root.collection("tests").whereArrayContains("studentIds",id),
            root.collection("examSessions").whereEqualTo("targetClassId",student.text("classId"))
        )
        val registrations = sources.map { query ->
            query.addSnapshotListener { snapshot, error ->
                if(error != null) failed("Live updates unavailable. Refresh to retry.")
                else if(snapshot != null && !snapshot.metadata.isFromCache) { changed() }
            }
        }
        var initialSchool = true
        registrations.plus(root.addSnapshotListener { snapshot, error ->
            if(error != null) failed("School settings could not be refreshed.")
            else if(snapshot != null && !snapshot.metadata.isFromCache) { if(!initialSchool) changed(); initialSchool = false }
        }).let { return it }
    }
}
private suspend fun <T> Task<T>.awaitStudent(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if(continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if(continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
