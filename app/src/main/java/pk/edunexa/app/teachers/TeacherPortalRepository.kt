package pk.edunexa.app.teachers
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class TeacherAccess(val profile: Map<String, Any?>, val config: Map<String, Any?>) {
    val currentSession get() = pk.edunexa.app.academic.AcademicSessionPolicy.current(config)
    val sessionId get() = currentSession["id"]?.toString().orEmpty()
    val sessionName get() = currentSession["name"]?.toString().orEmpty()
    val teacherId get() = profile["id"]?.toString().orEmpty()
    val assignments get() = DiaryPolicy.maps(config["teacherAssignments"]).filter { it["teacherId"] == teacherId && it["academicSession"] == sessionId }
    val inchargeClass get() = profile["inchargeClassId"]?.toString().orEmpty()
    val inchargeSection get() = profile["inchargeSectionId"]?.toString().orEmpty()
    val incharge get() = TeacherPortalPolicy.incharge(profile, config)
    fun name(kind: String, id: String) = DiaryPolicy.maps(config[kind]).firstOrNull { it["id"] == id }?.get("name")?.toString().orEmpty()
}
object TeacherPortalPolicy {
    val attendanceStatuses = setOf("Present", "Absent", "Leave", "Late")
    fun incharge(profile: Map<String, Any?>, config: Map<String, Any?>): Boolean {
        val current = pk.edunexa.app.academic.AcademicSessionPolicy.current(config)
        if (current["status"] !in setOf("Active", "Upcoming")) return false
        val cls = profile["inchargeClassId"]?.toString().orEmpty()
        val section = profile["inchargeSectionId"]?.toString().orEmpty()
        val scopeSession = profile["inchargeAcademicSession"]?.toString().orEmpty()
        return profile["status"] == "Active" && cls.isNotBlank() && section.isNotBlank() &&
            (scopeSession.isBlank() || scopeSession == current["id"]) &&
            DiaryPolicy.maps(config["classes"]).any { it["id"] == cls && it["isActive"] == true } &&
            DiaryPolicy.maps(config["sections"]).any { it["id"] == section && it["classId"] == cls && it["isActive"] == true }
    }
    fun roster(access: TeacherAccess, cls: String, section: String): List<DiaryItem> =
        DiaryPolicy.maps(access.config["studentRoster"]).filter {
            it["classId"] == cls && it["sectionId"] == section && it["status"] in setOf("Active", "On Leave") && it["isDeleted"] != true &&
            it["academicSessionId"]?.toString().orEmpty().ifBlank { it["academicSession"]?.toString().orEmpty() }.let { saved -> saved.isBlank() || saved in setOf(access.sessionId, access.sessionName) }
        }.map { DiaryItem(it["id"]?.toString().orEmpty(), it) }.filter { it.id.isNotBlank() }.distinctBy { it.id }
            .sortedWith(compareBy({ it.text("rollNo").toIntOrNull() ?: Int.MAX_VALUE }, { it.text("fullName") }))
    fun validateAttendance(access: TeacherAccess, date: String, today: String, statuses: Map<String, String>) {
        require(access.incharge) { "Attendance is available only for your current class-incharge assignment." }
        pk.edunexa.app.academic.AcademicPolicy.validateDates(date, date)
        require(date.isNotBlank() && date <= today) { "Choose today or an earlier attendance date." }
        val ids = roster(access, access.inchargeClass, access.inchargeSection).map { it.id }.toSet()
        require(ids.isNotEmpty() && ids.size < 400) { "The class has no active students or is too large for one save." }
        require(statuses.keys == ids && statuses.values.all { it in attendanceStatuses }) { "Mark every student. Refresh if the class roster changed." }
    }
    fun attendanceId(session: String, date: String, cls: String, section: String, student: String) =
        listOf(session,date,cls,section,student).joinToString("__") { it.trim().replace("/", "_").replace("\\", "_").replace(Regex("\\s+"), "-").take(120) }
    fun revision(row: DiaryItem?) = row?.text("lastUpdatedAt")?.ifBlank { row.text("updatedAt").ifBlank { row.text("markedAt") } }.orEmpty()
    fun latest(records: List<DiaryItem>) = records.groupBy { it.text("studentId") }.mapValues { (_, rows) -> rows.maxWithOrNull(compareBy({ revision(it) }, { it.id }))!! }
}
class TeacherAccessRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    suspend fun load(): TeacherAccess {
        check(actor.role == PortalRole.TEACHER && actor.profileId != null && FirebaseAuth.getInstance().currentUser?.uid == actor.uid) { "Active teacher login required." }
        val user = db.collection("users").document(actor.uid).get(Source.SERVER).teacherReady()
        check(user.getBoolean("active") == true && user.getBoolean("portalDisabled") != true && user.getString("role") == "teacher" && user.getString("schoolId") == actor.schoolId && user.getString("profileId") == actor.profileId) { "Teacher access changed. Sign in again." }
        val profile = db.collection("schools").document(actor.schoolId).collection("teachers").document(actor.profileId!!).get(Source.SERVER).teacherReady()
        check(profile.exists() && profile.getString("status") == "Active") { "This teacher profile is inactive." }
        val config = (profile.get("academicDirectory") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
        check(config.isNotEmpty()) { "Ask Admin to save your assignments to sync academic access." }
        return TeacherAccess(profile.data.orEmpty() + ("id" to profile.id), config)
    }
}
internal suspend fun <T> Task<T>.teacherReady(): T = suspendCoroutine { c ->
    addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.resumeWithException(IllegalStateException("Operation cancelled.")) }
}
