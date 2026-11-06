package pk.edunexa.app.teachers

import org.junit.Assert.*
import org.junit.Test

class StudentRosterUpdatePolicyTest {
    private val student = mapOf<String, Any?>("id" to "s1", "academicSessionId" to "2026", "admissionNo" to "123", "classId" to "c", "sectionId" to "a", "fullName" to "Ali")
    @Test fun contactAndStatusEditsKeepIdentity() {
        assertTrue(StudentRosterUpdatePolicy.canUpdateExisting(student, student + mapOf("fullName" to "Ahmed", "status" to "Inactive", "guardianPhone" to "123")))
        for (field in listOf("admissionNo", "rollNo", "classId", "sectionId", "academicSessionId")) assertFalse(StudentRosterUpdatePolicy.canUpdateExisting(student, student + (field to "changed")))
        assertFalse(StudentRosterUpdatePolicy.canUpdateExisting(student + ("isDeleted" to true), student))
        assertFalse(StudentRosterUpdatePolicy.canUpdateExisting(student + ("status" to "Inactive"), student + ("status" to "Active")))
    }
    @Test fun replacesOnlyTargetAndRemovesPrivateFields() {
        val other = mapOf<String, Any?>("id" to "s2", "fullName" to "Other")
        val result = StudentRosterUpdatePolicy.update(listOf(student, other), student + mapOf("fullName" to "Ahmed", "portalPassword" to "secret", "monthlyFee" to 500), true)
        assertEquals(other, result[1]); assertEquals("Ahmed", result[0]["fullName"])
        assertFalse(result[0].containsKey("portalPassword")); assertFalse(result[0].containsKey("monthlyFee"))
    }
    @Test fun membershipChangesDoNotDuplicateStudents() {
        val once = StudentRosterUpdatePolicy.update(emptyList(), student, true)
        assertEquals(once, StudentRosterUpdatePolicy.update(once, student, true))
        assertTrue(StudentRosterUpdatePolicy.update(once, student, false).isEmpty())
    }
}
