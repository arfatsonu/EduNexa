package pk.edunexa.app.academic

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.auth.LoginEmailPolicy
import pk.edunexa.app.auth.LoginGenerator
import pk.edunexa.app.auth.StudentLoginIdentity
import pk.edunexa.app.fees.FeeReference

class ReleaseRegressionTest {
    @Test fun activeSessionWithoutCurrentFlagIsVisibleAcrossPortals() {
        val config = mapOf("sessions" to listOf(
            mapOf("id" to "old", "name" to "2025", "status" to "Closed"),
            mapOf("id" to "opaque-uuid", "name" to "2026", "status" to "Active")))
        assertEquals("opaque-uuid", AcademicSessionPolicy.current(config)["id"])
        assertEquals("2026", AcademicSessionPolicy.current(config)["name"])
    }
    @Test fun explicitlyCurrentSessionWinsOverActiveFallback() {
        val config = mapOf("sessions" to listOf(
            mapOf("id" to "other", "status" to "Active"),
            mapOf("id" to "current", "status" to "Active", "isCurrent" to true)))
        assertEquals("current", AcademicSessionPolicy.current(config)["id"])
    }
    @Test fun emptyOrClosedOnlySetupDoesNotInventCurrentSession() {
        assertTrue(AcademicSessionPolicy.current(emptyMap()).isEmpty())
        assertTrue(AcademicSessionPolicy.current(mapOf("sessions" to listOf(mapOf("id" to "old", "status" to "Closed")))).isEmpty())
    }
    @Test fun sameFirstNameStillProducesDifferentEmailsForDifferentRegistrations() {
        assertNotEquals(StudentLoginIdentity.email("Muhammad Tariq", "STU-011", "ANS"), StudentLoginIdentity.email("Muhammad Ali", "STU-012", "ANS"))
    }
    @Test fun customLoginEmailIsUsedByActualCredentialGenerator() {
        val login = LoginGenerator.generate("student", "school", "profile", "STU-011", "Muhammad Tariq", "ANS", "  TARIQ11@ANS.EDU  ")
        assertEquals("tariq11@ans.edu", login.email)
        assertTrue(login.password.length >= 6)
    }
    @Test fun customEmailDoesNotRequireEnglishFirstName() {
        assertEquals("teacher4@ans.edu", LoginGenerator.generate("teacher", "school", "profile", "T-004", "اقرا", "ANS", "teacher4@ans.edu").email)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMalformedCustomEmail() {
        LoginEmailPolicy.choose("name with spaces@ans.edu", "fallback@ans.edu")
    }
    @Test fun assignmentsKeepOtherClassAndOtherSessionIntact() {
        val otherClass = mapOf<String, Any?>("id" to "a", "classId" to "other", "academicSession" to "s", "subjectId" to "math")
        val otherSession = otherClass + mapOf("id" to "b", "classId" to "ours", "academicSession" to "old")
        val added = SubjectPolicy.assign(listOf(otherClass, otherSession), "ours", "s", "math", true, "new")
        assertEquals(3, added.size)
        assertEquals(listOf(otherClass, otherSession), SubjectPolicy.assign(added, "ours", "s", "math", false, "unused"))
    }
    @Test fun repeatedSubjectSelectionDoesNotDuplicateSavedAssignment() {
        val first = SubjectPolicy.assign(emptyList(), "class", "session", "subject", true, "id1")
        assertEquals(first, SubjectPolicy.assign(first, "class", "session", "subject", true, "id2"))
    }
    @Test fun voucherFormattingPreservesAllReferenceCharacters() {
        assertEquals("VCH-20C18A92FCB", FeeReference.display("VCH-N-20c18a92-fcb"))
        assertEquals("Legacy-123", FeeReference.display("Legacy-123"))
        assertEquals("15 Oct 2026", FeeReference.date("2026-10-15"))
        assertEquals("bad-date", FeeReference.date("bad-date"))
    }
}
