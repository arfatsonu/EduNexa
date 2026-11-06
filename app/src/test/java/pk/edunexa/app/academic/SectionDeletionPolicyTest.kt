package pk.edunexa.app.academic

import org.junit.Assert.*
import org.junit.Test

class SectionDeletionPolicyTest {
    private val section = mapOf<String, Any?>("id" to "s", "isActive" to false)
    private fun allowed(students: List<Map<String, Any?>> = emptyList(), teachers: List<Map<String, Any?>> = emptyList(), assignments: List<Map<String, Any?>> = emptyList()): Boolean =
        runCatching { AcademicPolicy.validateSectionDeletion(section, students, teachers, assignments, "2026") }.isSuccess
    @Test fun activeAndLeaveStudentsBlockDeletion() {
        for (status in listOf("Active", "On Leave")) assertFalse(allowed(students = listOf(mapOf("sectionId" to "s", "status" to status))))
        assertTrue(allowed(students = listOf(mapOf("sectionId" to "other", "status" to "Active"))))
    }
    @Test fun currentResponsibilitiesBlockButHistoricalLinksRemainAllowed() {
        assertFalse(allowed(teachers = listOf(mapOf("inchargeSectionId" to "s", "status" to "Active", "inchargeAcademicSession" to "2026"))))
        assertFalse(allowed(assignments = listOf(mapOf("sectionId" to "s", "academicSession" to "2026"))))
        assertTrue(allowed(students = listOf(mapOf("sectionId" to "s", "status" to "Inactive")), assignments = listOf(mapOf("sectionId" to "s", "academicSession" to "2025"))))
    }
    @Test fun activeAndMissingSectionsCannotBeDeleted() {
        assertTrue(runCatching { AcademicPolicy.validateSectionDeletion(section + ("isActive" to true), emptyList(), emptyList(), emptyList(), "2026") }.isFailure)
        assertTrue(runCatching { AcademicPolicy.validateSectionDeletion(emptyMap(), emptyList(), emptyList(), emptyList(), "2026") }.isFailure)
    }
}
