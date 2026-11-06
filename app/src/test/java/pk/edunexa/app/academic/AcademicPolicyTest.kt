package pk.edunexa.app.academic

import org.junit.Assert.*
import org.junit.Test

class AcademicPolicyTest {
    private val inactive = mapOf<String, Any?>("id" to "old", "isActive" to false)
    @Test fun inactiveSectionWithHistoricalStudentsCanBeDeleted() {
        AcademicPolicy.validateSectionDeletion(inactive, listOf(mapOf("sectionId" to "old", "status" to "Inactive")), emptyList(), emptyList(), "current")
    }
    @Test(expected = IllegalArgumentException::class) fun activeSectionDeletionRejected() {
        AcademicPolicy.validateSectionDeletion(inactive + ("isActive" to true), emptyList(), emptyList(), emptyList(), "current")
    }
    @Test(expected = IllegalArgumentException::class) fun activeStudentBlocksDeletion() {
        AcademicPolicy.validateSectionDeletion(inactive, listOf(mapOf("sectionId" to "old", "status" to "On Leave")), emptyList(), emptyList(), "current")
    }
    @Test(expected = IllegalArgumentException::class) fun currentAssignmentBlocksDeletion() {
        AcademicPolicy.validateSectionDeletion(inactive, emptyList(), emptyList(), listOf(mapOf("sectionId" to "old", "academicSession" to "current")), "current")
    }
    @Test(expected = IllegalArgumentException::class) fun currentInchargeBlocksDeletion() {
        AcademicPolicy.validateSectionDeletion(inactive, emptyList(), listOf(mapOf("inchargeSectionId" to "old", "status" to "Active", "inchargeAcademicSession" to "current")), emptyList(), "current")
    }

    @Test fun switchingCurrentPreservesIdentityAndDates() {
        val old = AcademicEntry(id = "old", name = "2026-2027", status = "Active", isCurrent = true)
        val next = AcademicEntry(id = "next", name = "2027-2028", startDate = "2027-04-01")
        val result = AcademicPolicy.selectCurrent(listOf(old, next), "next")
        assertEquals(1, result.count { it.isCurrent })
        assertEquals(old.copy(isCurrent = false), result[0])
        assertEquals(next.copy(isCurrent = true, status = "Active"), result[1])
    }
    @Test(expected = IllegalArgumentException::class) fun closedSessionCannotBecomeCurrent() {
        AcademicPolicy.selectCurrent(listOf(AcademicEntry(id = "closed", status = "Closed")), "closed")
    }
    @Test(expected = IllegalArgumentException::class) fun missingSessionCannotBecomeCurrent() {
        AcademicPolicy.selectCurrent(emptyList(), "missing")
    }
    @Test fun editingSameIdentityAllowsName() {
        AcademicPolicy.validateName(" Class 1 ", "one", listOf(AcademicEntry(id = "one", name = "Class 1")))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateNamesIgnoreCaseAndWhitespace() {
        AcademicPolicy.validateName(" class 1 ", "two", listOf(AcademicEntry(id = "one", name = "Class 1")))
    }
    @Test fun validLeapDateAndOptionalDates() {
        AcademicPolicy.validateDates("2028-02-29", "2028-03-01")
        AcademicPolicy.validateDates("", "")
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCalendarDateRejected() {
        AcademicPolicy.validateDates("2027-02-29", "2027-03-01")
    }
    @Test(expected = IllegalArgumentException::class) fun reversedDatesRejected() {
        AcademicPolicy.validateDates("2028-04-01", "2028-03-01")
    }
}
