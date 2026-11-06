package pk.edunexa.app.notifications

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.students.StudentPortalData

class StudentActivityAlertsTest {
    private val pupil=DiaryItem("one",mapOf("status" to "Active","classId" to "c","sectionId" to "s","academicSessionId" to "year"))
    private val stamp="2026-10-12T10:00:00.000Z"
    private fun data(attendance: List<DiaryItem> = emptyList(), fees: List<DiaryItem> = emptyList(), diary: List<DiaryItem> = emptyList(), feedback: List<DiaryItem> = emptyList(), student: DiaryItem = pupil) = StudentPortalData(student,"School",attendance,diary,feedback,emptyList(),emptyList(),fees,emptyList())
    private fun attendance(status: String="Absent", school: String="a",student: String="one") = DiaryItem("row",mapOf("schoolId" to school,"studentId" to student,"status" to status,"academicSessionId" to "year","attendanceDate" to "2026-10-12","markedAt" to stamp))
    private fun homework(extra: Map<String,Any?> = emptyMap()) = DiaryItem("diary",mapOf("schoolId" to "a","classId" to "c","sectionId" to "s","academicSessionId" to "year","studentIds" to listOf("one"),"homework" to "Exercise 1","subject" to "Math","updatedAt" to stamp)+extra)
    @Test fun attendanceIncludesAbsentAndAllStatusesWithoutNoticeDocument() {
        for(status in listOf("Present","Absent","Leave","Late")) {
            val alert=StudentActivityAlerts.rows(data(attendance=listOf(attendance(status))),"a").single()
            assertTrue(alert.text("message").contains(status));assertEquals(listOf("one"),alert.raw["recipientIds"])
            assertTrue(NotificationPolicy.visible(alert,false,"one",Long.MAX_VALUE-1))
            assertFalse(NotificationPolicy.visible(alert,false,"two",Long.MAX_VALUE-1))
        }
    }
    @Test fun statusChangeCreatesNewUnreadIdButRemarksDoNot() {
        val original=attendance();val before=StudentActivityAlerts.rows(data(attendance=listOf(original)),"a").single()
        val remarks=DiaryItem(original.id,original.raw+mapOf("remarks" to "Called parent","lastUpdatedAt" to "2026-10-12T11:00:00.000Z"))
        assertEquals(before.id,StudentActivityAlerts.rows(data(attendance=listOf(remarks)),"a").single().id)
        assertNotEquals(before.id,StudentActivityAlerts.rows(data(attendance=listOf(attendance("Present"))),"a").single().id)
    }
    @Test fun feeAlertsRequireActualNewPaymentAndKeepPartialReceiptsSeparate() {
        val p=mapOf("id" to "p1","amount" to 500,"receiptNo" to "R1","createdAt" to stamp)
        val bill=DiaryItem("fee",mapOf("studentId" to "one","feeType" to "Tuition Fee","paymentHistory" to listOf(p,p,p+mapOf("id" to "p2","amount" to 200,"receiptNo" to "R2"))))
        val alerts=StudentActivityAlerts.rows(data(fees=listOf(bill)),"a")
        assertEquals(2,alerts.size);assertTrue(alerts.any { it.text("message").contains("500") && it.text("message").contains("R1") })
        val unpaid=DiaryItem("unpaid",bill.raw+mapOf("paymentHistory" to emptyList<Any>()))
        assertTrue(StudentActivityAlerts.rows(data(fees=listOf(unpaid)),"a").isEmpty())
        assertEquals(alerts.map { it.id }.toSet(),StudentActivityAlerts.rows(data(fees=listOf(DiaryItem(bill.id,bill.raw+mapOf("updatedAt" to "later")))),"a").map { it.id }.toSet())
    }
    @Test fun feeAlertSurvivesOlderAcademicSessionButNotDifferentStudentOrSchool() {
        val fee=DiaryItem("f",mapOf("schoolId" to "a","studentId" to "one","academicSessionId" to "old","paymentHistory" to listOf(mapOf("id" to "p","amount" to 100,"paymentDate" to "2026-10-12"))))
        assertEquals(1,StudentActivityAlerts.rows(data(fees=listOf(fee)),"a").size)
        assertTrue(StudentActivityAlerts.rows(data(fees=listOf(DiaryItem(fee.id,fee.raw+mapOf("studentId" to "two")))),"a").isEmpty())
        assertTrue(StudentActivityAlerts.rows(data(fees=listOf(fee)),"b").isEmpty())
    }
    @Test fun homeworkRequiresSavedRecipientAndMatchingClassSession() {
        assertEquals(1,StudentActivityAlerts.rows(data(diary=listOf(homework())),"a").size)
        for(extra in listOf(mapOf("studentIds" to listOf("two")),mapOf("sectionId" to "other"),mapOf("academicSessionId" to "old"),mapOf("status" to "Draft"),mapOf("homework" to ""))) assertTrue(StudentActivityAlerts.rows(data(diary=listOf(homework(extra))),"a").isEmpty())
    }
    @Test fun homeworkMetadataSaveDoesNotResetReadButContentChangeDoes() {
        fun id(extra: Map<String,Any?> = emptyMap()) = StudentActivityAlerts.rows(data(diary=listOf(homework(extra))),"a").single().id
        assertEquals(id(),id(mapOf("updatedAt" to "2026-10-13T10:00:00.000Z","homeworkCheckId" to "hw")))
        assertNotEquals(id(),id(mapOf("homework" to "Exercise 2")))
    }
    @Test fun checkedHomeworkIsOwnStudentOnly() {
        val feedback=DiaryItem("check",mapOf("schoolId" to "a","studentId" to "one","academicSessionId" to "year","checkedAt" to stamp,"record" to mapOf("status" to "Completed"),"context" to mapOf("subject" to "Math")))
        val rows=StudentActivityAlerts.rows(data(feedback=listOf(feedback)),"a")
        assertEquals("Homework checked",rows.single().text("title"))
        assertTrue(StudentActivityAlerts.rows(data(feedback=listOf(DiaryItem(feedback.id,feedback.raw+mapOf("studentId" to "two")))),"a").isEmpty())
    }
    @Test fun inactiveDeletedWrongSchoolAndOtherStudentsAreExcluded() {
        for(extra in listOf(mapOf("status" to "Inactive"),mapOf("isDeleted" to true))) assertTrue(StudentActivityAlerts.rows(data(attendance=listOf(attendance()),student=DiaryItem("one",pupil.raw+extra)),"a").isEmpty())
        assertTrue(StudentActivityAlerts.rows(data(attendance=listOf(attendance(student="two"))),"a").isEmpty())
        assertTrue(StudentActivityAlerts.rows(data(attendance=listOf(attendance(school="b"))),"a").isEmpty())
    }
    @Test fun duplicateAttendanceDocsShowLatestSavedStatusOnly() {
        val old=attendance();val updated=DiaryItem("new",attendance("Present").raw+mapOf("markedAt" to "2026-10-12T11:00:00.000Z"))
        val rows=StudentActivityAlerts.rows(data(attendance=listOf(old,updated)),"a")
        assertEquals(1,rows.size);assertTrue(rows.single().text("message").contains("Present"))
    }
    @Test fun newerUnmarkedAttendanceSuppressesOldAlert() {
        val old=attendance()
        val reset=DiaryItem("reset",old.raw+mapOf("status" to "Unmarked","lastUpdatedAt" to "2026-10-12T12:00:00.000Z"))
        assertTrue(StudentActivityAlerts.rows(data(attendance=listOf(old,reset)),"a").isEmpty())
    }
    @Test fun correctedAttendanceUsesCorrectionTime() {
        val correction=DiaryItem("row",attendance("Present").raw+mapOf("lastUpdatedAt" to "2026-10-12T12:00:00.000Z"))
        assertEquals("2026-10-12T12:00:00.000Z",StudentActivityAlerts.rows(data(attendance=listOf(correction)),"a").single().text("publishAt"))
    }
    @Test fun schoolIdsIsolateAlertIdentitiesAndServerActivityDoesNotDuplicate() {
        val local=StudentActivityAlerts.rows(data(attendance=listOf(attendance())),"a")
        val elsewhere=StudentActivityAlerts.rows(data(attendance=listOf(attendance(school="b"))),"b")
        assertNotEquals(local.single().id,elsewhere.single().id)
        val server=DiaryItem("server",mapOf("sourceKind" to "attendance","sourceId" to "row"))
        assertEquals(listOf(server),StudentActivityAlerts.merge(listOf(server),local))
    }
}
