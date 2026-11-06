package pk.edunexa.app.students

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem

class StudentPortalPolicyTest {
    private fun attendance(id: String,date: String,status: String,updated: String) = DiaryItem(id,mapOf("attendanceDate" to date,"status" to status,"lastUpdatedAt" to updated))
    @Test fun deduplicateMonthlyAttendanceBySavedRevision() {
        val rows = StudentPortalPolicy.attendance(listOf(attendance("old","2026-10-01","Absent","2026-10-01"),attendance("new","2026-10-01","Present","2026-10-02"),attendance("sep","2026-09-30","Present",""),attendance("invalid","2026-10-02","Unknown","")),"2026-10")
        assertEquals(listOf("new"),rows.map { it.id })
        assertEquals("100%",StudentPortalPolicy.attendancePercent(rows))
        assertEquals("—",StudentPortalPolicy.attendancePercent(emptyList()))
    }
    @Test fun newerUnmarkedRecordDoesNotReviveAnOlderMarkedStatus() {
        val rows = StudentPortalPolicy.attendance(listOf(
            attendance("old", "2026-10-01", "Present", "2026-10-01T03:00:00Z"),
            attendance("reset", "2026-10-01", "Unmarked", "2026-10-01T04:00:00Z")
        ), "2026-10")
        assertTrue(rows.isEmpty())
        assertEquals("—", StudentPortalPolicy.attendancePercent(rows))
    }
    @Test fun lateCountsAttendedAndLeaveDoesNot() {
        assertEquals("50%",StudentPortalPolicy.attendancePercent(listOf(attendance("a","2026-10-01","Late",""),attendance("b","2026-10-02","Leave",""))))
    }
    @Test fun personalFeedbackContainsOnlyItsOwnRecord() {
        val source = mapOf("schoolId" to "school","diaryId" to "diary","academicSessionId" to "session","checkedAt" to "stamp","records" to listOf(mapOf("studentId" to "one","status" to "Completed"),mapOf("studentId" to "two","status" to "Not Done")))
        val feedback = StudentPortalPolicy.feedbackPayload(source,"homework",0)
        assertEquals("one",feedback["studentId"])
        assertFalse(feedback.containsKey("records"))
        assertEquals("Completed",StudentPortalPolicy.map(feedback["record"])["status"])
        assertEquals("homework__one",StudentPortalPolicy.feedbackId("homework","one"))
    }
    @Test fun sessionAndClassFiltersUseCanonicalSavedIds() {
        val student = DiaryItem("one",mapOf("classId" to "c","sectionId" to "s","academicSessionId" to "now"))
        fun diary(id: String,session: String,section: String) = DiaryItem(id,mapOf("classId" to "c","sectionId" to section,"academicSessionId" to session,"homework" to "Task"))
        val data = StudentPortalData(student,"School",emptyList(),listOf(diary("own","now","s"),diary("old","past","s"),diary("other","now","t")),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        assertEquals(listOf("own"),data.currentDiary.map { it.id })
        assertEquals(1,data.pendingHomework)
    }
    @Test fun opaqueSessionIdNeverAppearsInStudentDisplay() {
        val id="ee1f594c-756b-4ce9-b468-b403db40cd58"
        fun data(label: String) = StudentPortalData(DiaryItem("s",mapOf("academicSessionId" to id,"academicSession" to id,"academicSessionName" to label)),"School",emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        assertEquals("Academic session",data("").sessionLabel)
        assertEquals("2026–2027",data("2026–2027").sessionLabel)
        assertTrue(data("2026–2027").inSession(DiaryItem("d",mapOf("academicSessionId" to id))))
        assertFalse(StudentPortalPolicy.readableSession(id))
    }
    @Test fun savedZeroMarksRemainZeroAndAbsenceNeverBecomesZero() {
        assertEquals("0 / 20",StudentPortalPolicy.resultScore(DiaryItem("r",mapOf("status" to "Present","marksObtained" to 0,"totalMarks" to 20))))
        assertEquals("Absent",StudentPortalPolicy.resultScore(DiaryItem("r",mapOf("status" to "Absent"))))
        assertEquals("Not entered",StudentPortalPolicy.resultScore(DiaryItem("r",mapOf("status" to "Present"))))
    }
}
