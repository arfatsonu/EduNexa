package pk.edunexa.app.teachers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pk.edunexa.app.attendance.TeacherAttendanceData
import pk.edunexa.app.attendance.TeacherAttendanceRepository
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.*
import pk.edunexa.app.exams.ExamData
import pk.edunexa.app.exams.ExamRepository
import pk.edunexa.app.fees.FeePolicy

data class TeacherDashboardData(val attendance: TeacherAttendanceData, val diary: DiaryData, val exams: ExamData) {
    val access get() = attendance.access
    val assignments get() = access.assignments.distinctBy { listOf(it["classId"],it["sectionId"],it["subjectId"]) }
    val classScopes get() = assignments.map { it["classId"] to it["sectionId"] }.distinct()
    val ownDiary get() = diary.entries.filter { it.text("teacherId") == access.teacherId && it.text("academicSessionId") == access.sessionId }
    val todayDiary get() = ownDiary.filter { it.text("date") == attendance.date }
    val pendingHomework get() = TeacherDashboardPolicy.pendingHomework(ownDiary,diary.checks,attendance.date)
    val todayTests get() = exams.tests.filter { it.text("teacherId") == access.teacherId && it.text("academicSessionId") == access.sessionId && it.text("date") == attendance.date && it.text("status") != "Cancelled" }
    val pendingTests get() = exams.tests.filter { test ->
        test.text("teacherId") == access.teacherId && test.text("academicSessionId") == access.sessionId && test.text("date") <= attendance.date && test.text("status") != "Cancelled" &&
        !pk.edunexa.app.exams.ExamPolicy.complete(exams.rows(test).map { it.raw }, (test.raw["studentIds"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: exams.roster(test).map { it.id }.toSet(),(test.raw["totalMarks"] as? Number)?.toDouble() ?: 0.0)
    }
}
object TeacherDashboardPolicy {
    fun pendingHomework(entries: List<DiaryItem>, checks: List<DiaryItem>, today: String): Int =
        entries.count { entry ->
            if(entry.text("homework").isBlank() || entry.text("dueDate").ifBlank { entry.text("date") } > today) false
            else {
                val expected = (entry.raw["studentIds"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
                val records = DiaryPolicy.maps(checks.firstOrNull { it.text("diaryId") == entry.id }?.raw?.get("records"))
                expected.isEmpty() || expected.any { id -> records.none { it["studentId"] == id && it["status"] in (DiaryPolicy.statuses - "Not Checked") } }
            }
        }
}
class TeacherDashboardRepository(private val actor: PortalSession) {
    suspend fun load(): TeacherDashboardData = coroutineScope {
        val attendance = async { TeacherAttendanceRepository(actor).load(FeePolicy.today()) }
        val diary = async { TeacherDiaryRepository(actor).load(actor.schoolId) }
        val exams = async { ExamRepository(actor).load() }
        TeacherDashboardData(attendance.await(),diary.await(),exams.await())
    }
}
