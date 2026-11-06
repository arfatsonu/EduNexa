package pk.edunexa.app.teachers
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy

class TeacherPortalPolicyTest {
    private val config = mapOf<String,Any?>(
        "sessions" to listOf(mapOf("id" to "s","name" to "2026–2027","isCurrent" to true,"status" to "Active")),
        "classes" to listOf(mapOf("id" to "c","isActive" to true)),
        "sections" to listOf(mapOf("id" to "sec","classId" to "c","isActive" to true)),
        "studentRoster" to listOf(
            mapOf("id" to "a","classId" to "c","sectionId" to "sec","status" to "Active","academicSessionId" to "s"),
            mapOf("id" to "b","classId" to "c","sectionId" to "sec","status" to "On Leave","academicSessionId" to "2026–2027"),
            mapOf("id" to "missing","classId" to "c","sectionId" to "sec","status" to "Active"),
            mapOf("id" to "other","classId" to "c","sectionId" to "outside","status" to "Active"),
            mapOf("id" to "old","classId" to "c","sectionId" to "sec","status" to "Active","academicSessionId" to "old"),
            mapOf("id" to "inactive","classId" to "c","sectionId" to "sec","status" to "Inactive"),
            mapOf("id" to "deleted","classId" to "c","sectionId" to "sec","status" to "Active","isDeleted" to true)
        ))
    private val profile = mapOf<String,Any?>("id" to "teacher","status" to "Active","inchargeClassId" to "c","inchargeSectionId" to "sec","inchargeAcademicSession" to "s")
    private fun access(p: Map<String,Any?> = profile) = TeacherAccess(p,config)
    @Test fun currentInchargeCanOnlyUseItsOwnRoster() {
        assertTrue(access().incharge)
        assertEquals(setOf("a","b","missing"),TeacherPortalPolicy.roster(access(),"c","sec").map { it.id }.toSet())
    }
    @Test fun subjectAssignmentDoesNotGrantInchargePermission() {
        assertFalse(access(profile - "inchargeClassId").incharge)
        assertFalse(access(profile + ("inchargeAcademicSession" to "old")).incharge)
        assertFalse(access(profile + ("status" to "Inactive")).incharge)
    }
    @Test fun completeStatusesRequiredAndNoFutureAttendance() {
        val statuses = mapOf("a" to "Present","b" to "Leave","missing" to "Absent")
        TeacherPortalPolicy.validateAttendance(access(),"2026-10-10","2026-10-10",statuses)
        assertTrue(runCatching { TeacherPortalPolicy.validateAttendance(access(),"2026-10-11","2026-10-10",statuses) }.isFailure)
        assertTrue(runCatching { TeacherPortalPolicy.validateAttendance(access(),"2026-10-10","2026-10-10",statuses - "a") }.isFailure)
        assertTrue(runCatching { TeacherPortalPolicy.validateAttendance(access(),"2026-10-10","2026-10-10",statuses + ("outside" to "Present")) }.isFailure)
    }
    @Test fun canonicalAttendanceIdMatchesAdminAndIsScopeSpecific() {
        assertEquals("s__2026-10-10__c__sec__a",TeacherPortalPolicy.attendanceId("s","2026-10-10","c","sec","a"))
        assertNotEquals(TeacherPortalPolicy.attendanceId("s","d","c","sec","a"),TeacherPortalPolicy.attendanceId("other","d","c","sec","a"))
    }
    @Test fun latestAttendanceAvoidsCountingDuplicateLegacyRecords() {
        val rows = listOf(DiaryItem("old",mapOf("studentId" to "a","markedAt" to "2026-10-10T08:00:00Z","status" to "Absent")),DiaryItem("new",mapOf("studentId" to "a","lastUpdatedAt" to "2026-10-10T09:00:00Z","status" to "Present")))
        assertEquals("Present",TeacherPortalPolicy.latest(rows).getValue("a").text("status"))
        assertEquals(1,TeacherPortalPolicy.latest(rows).size)
    }
    @Test fun homeworkCountsOnlyDueTasksWithOutstandingStudents() {
        val task = DiaryItem("task",mapOf("homework" to "Practice","dueDate" to "2026-10-10","studentIds" to listOf("a","b")))
        val future = task.copy(id = "future",raw = task.raw + ("dueDate" to "2026-10-11"))
        fun check(status: String) = DiaryItem("check",mapOf("diaryId" to "task","records" to listOf(mapOf("studentId" to "a","status" to "Completed"),mapOf("studentId" to "b","status" to status))))
        assertEquals(1,TeacherDashboardPolicy.pendingHomework(listOf(task,future),listOf(check("Not Checked")),"2026-10-10"))
        assertEquals(0,TeacherDashboardPolicy.pendingHomework(listOf(task,future),listOf(check("Not Done")),"2026-10-10"))
    }
}
