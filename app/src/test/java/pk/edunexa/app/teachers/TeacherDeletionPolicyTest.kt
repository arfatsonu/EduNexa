package pk.edunexa.app.teachers

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.users.ManagedAccountPolicy

class TeacherDeletionPolicyTest {
    @Test fun removesOnlySelectedTeachersAssignmentsAcrossSessions() {
        val assignments = listOf(
            mapOf<String, Any?>("teacherId" to "old", "academicSession" to "2025", "id" to "a"),
            mapOf<String, Any?>("teacherId" to "old", "academicSession" to "2026", "id" to "b"),
            mapOf<String, Any?>("teacherId" to "other", "academicSession" to "2026", "id" to "c")
        )
        assertEquals(listOf(assignments[2]), TeacherDeletionPolicy.remainingAssignments(assignments, "old"))
        assertEquals(3, assignments.size)
    }

    @Test fun ordinaryTeacherKeepsNormalLoginEmail() {
        assertEquals("haba.t001@ans.edu", TeacherDeletionPolicy.availableLoginEmail(
            "haba.t001@ans.edu", "new-profile", emptySet()))
    }

    @Test fun reusedEmployeeIdGetsSeparateLoginWithoutReactivatingOldAccount() {
        val reserved = setOf("haba.t001@ans.edu")
        val email = TeacherDeletionPolicy.availableLoginEmail("haba.t001@ans.edu", "fresh-123", reserved)
        assertEquals("haba.t001.2@ans.edu", email)
        assertFalse(email in reserved)
    }

    @Test fun deletedTeacherAndLinkedLoginStayOutOfUserManagement() {
        val users = listOf(
            DiaryItem("old-login", mapOf("role" to "teacher", "profileId" to "old", "active" to false, "portalDisabled" to true)),
            DiaryItem("current-login", mapOf("role" to "teacher", "profileId" to "current", "active" to true))
        )
        val teachers = listOf(
            DiaryItem("old", mapOf("isDeleted" to true, "authUid" to "old-login", "status" to "Inactive")),
            DiaryItem("current", mapOf("authUid" to "current-login", "status" to "Active", "name" to "Current teacher"))
        )
        val accounts = ManagedAccountPolicy.build(users, teachers, emptyList())
        assertEquals(1, accounts.size)
        assertEquals("current", accounts.single().profileId)
        assertTrue(accounts.single().manageable)
    }
}
