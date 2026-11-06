package pk.edunexa.app.fees

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem

class FeeVisibilityTest {
    @Test fun removedAndMissingStudentsDoNotContributeToCurrentFees() {
        val students=listOf(DiaryItem("live",mapOf("status" to "Active")),DiaryItem("removed",mapOf("isDeleted" to true)))
        val ids=FeeVisibility.currentStudentIds(students)
        assertTrue(FeeVisibility.isCurrent(mapOf("studentId" to "live"),ids))
        assertFalse(FeeVisibility.isCurrent(mapOf("studentId" to "removed"),ids))
        assertFalse(FeeVisibility.isCurrent(mapOf("studentId" to "missing"),ids))
        assertFalse(FeeVisibility.isCurrent(emptyMap(),ids))
        assertFalse(FeeVisibility.isCurrent(mapOf("studentId" to ""),ids))
    }
    @Test fun inactiveProfilesKeepTheirDuesUntilActuallyRemoved() {
        val students=listOf(DiaryItem("inactive",mapOf("status" to "Inactive")),DiaryItem("leave",mapOf("status" to "On Leave")))
        val ids=FeeVisibility.currentStudentIds(students)
        assertEquals(setOf("inactive","leave"),ids)
        assertTrue(FeeVisibility.isCurrent(mapOf("studentId" to "inactive"),ids))
    }
    @Test fun removingEveryStudentLeavesZeroCurrentBillsAndRetainsHistory() {
        val students=listOf(DiaryItem("s",mapOf("isDeleted" to true)))
        val records=listOf(mapOf<String,Any?>("studentId" to "s","netPayable" to 2800.0,"paidAmount" to 500.0))
        val ids=FeeVisibility.currentStudentIds(students)
        assertEquals(0,records.count { FeeVisibility.isCurrent(it,ids) })
        assertEquals(1,records.count { !FeeVisibility.isCurrent(it,ids) })
        assertEquals(500.0,records[0]["paidAmount"])
    }
}
