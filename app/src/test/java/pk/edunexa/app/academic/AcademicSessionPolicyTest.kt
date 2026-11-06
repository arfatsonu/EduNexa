package pk.edunexa.app.academic
import org.junit.Assert.*
import org.junit.Test
class AcademicSessionPolicyTest {
    @Test fun opaqueIdsMustNeverCollideByDigits() {
        assertFalse(AcademicSessionPolicy.matches("session_alpha", listOf("session_beta")))
        assertFalse(AcademicSessionPolicy.matches("year-12-old", listOf("year-12-new")))
        assertTrue(AcademicSessionPolicy.matches("session_alpha", listOf("session_alpha", "2026–2027")))
    }
    @Test fun onlyConfiguredAcademicYearAliasesMatch() {
        assertTrue(AcademicSessionPolicy.matches("2026-2027", listOf("uuid", "2026–2027")))
        assertFalse(AcademicSessionPolicy.matches("2025–2026", listOf("uuid", "2026–2027")))
        assertFalse(AcademicSessionPolicy.matches("", listOf("uuid")))
        assertTrue(AcademicSessionPolicy.matches("", listOf("uuid"), true))
        assertFalse(AcademicSessionPolicy.matches("uuid", emptyList()))
    }
    @Test fun onLeaveRemainsEnrolled() {
        assertTrue(AcademicSessionPolicy.enrolled("On Leave"))
        assertFalse(AcademicSessionPolicy.enrolled("Inactive"))
    }
}
