package pk.edunexa.app.teachers

import org.junit.Assert.*
import org.junit.Test

class StudentSavePolicyTest {
    private val saved = mapOf<String, Any?>("fullName" to "Student", "admissionNo" to "A", "rollNo" to "1", "classId" to "c", "sectionId" to "s", "className" to "Class 6", "section" to "Boys", "status" to "Active", "gender" to "Male", "guardianPhone" to "03000000000", "academicSession" to "current", "academicSessionId" to "current")
    @Test fun nonRosterEditsDoNotRebuildTeacherPermissions() {
        assertTrue(StudentSavePolicy.canSkipDirectory(saved, saved + mapOf("monthlyFee" to 2800.0, "profilePhoto" to "new-photo", "address" to "Updated address")))
    }
    @Test fun scopeIdentityAndContactChangesRequireDirectorySync() {
        listOf("fullName", "admissionNo", "rollNo", "classId", "sectionId", "status", "guardianPhone", "academicSessionId").forEach { field ->
            assertFalse(field, StudentSavePolicy.canSkipDirectory(saved, saved + (field to "changed")))
        }
    }
    @Test fun legacyMissingSessionMustUseFullValidation() {
        val legacy = saved - "academicSessionId"
        assertFalse(StudentSavePolicy.canSkipDirectory(legacy, legacy))
    }
}
