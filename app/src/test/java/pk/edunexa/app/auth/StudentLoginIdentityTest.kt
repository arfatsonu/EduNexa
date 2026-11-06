package pk.edunexa.app.auth

import org.junit.Assert.*
import org.junit.Test

class StudentLoginIdentityTest {
    @Test fun firstNameAndRegistrationUseSchoolShortForm() {
        assertEquals("ali000102@ans.edu", StudentLoginIdentity.email("Ali Ahmad", "STU-000102", "ANS"))
        assertEquals("ali102@anps.edu", StudentLoginIdentity.email(" Ali Ahmad ", "102", " anps "))
    }
    @Test fun registrationDigitsIncludingLeadingZerosArePreserved() {
        assertEquals("khizar008@ans.edu", StudentLoginIdentity.email("Khizar Ahmad", "STU-008", "ANS"))
        assertEquals("khizar008@ans.edu", StudentLoginIdentity.email("Khizar Ahmad", "008", "ANS"))
        assertEquals("ali000@anps.edu", StudentLoginIdentity.email("Ali", "STU-000", "ANPS"))
        assertNotEquals(StudentLoginIdentity.email("Ali", "008", "ANS"), StudentLoginIdentity.email("Ali", "8", "ANS"))
    }
    @Test fun shortFormOnlyChangesStudentEmailAndPreservesPassword() {
        val oldPassword = LoginGenerator.password("student", "school-001", "student-id", "STU-102", "Ali Ahmad")
        val updated = LoginGenerator.generate("student", "school-001", "student-id", "STU-102", "Ali Ahmad", "ANS")
        assertEquals("Ali102", oldPassword)
        assertEquals(oldPassword, updated.password)
        assertEquals("ali102@ans.edu", updated.email)
        assertEquals("Ali102", LoginGenerator.generate("teacher", "school-001", "teacher", "EMP-102", "Ali", "ANS").password)
    }
    @Test(expected = IllegalArgumentException::class) fun generatorCannotFallBackToHashedStudentEmail() {
        LoginGenerator.generate("student", "school", "student", "STU-6", "Khizar Ahmad")
    }
    @Test fun newLoginHasNoLegacySchoolDigest() {
        val login = LoginGenerator.generate("student", "school", "student", "STU-006", "Khizar Ahmad", "ANS")
        assertEquals("khizar006@ans.edu", login.email)
        assertEquals("Khizar006", login.password)
    }
    @Test(expected = IllegalArgumentException::class) fun missingSchoolShortFormRejected() {
        StudentLoginIdentity.email("Ali", "102", "")
    }
    @Test(expected = IllegalArgumentException::class) fun domainInjectionRejected() {
        StudentLoginIdentity.email("Ali", "102", "ANS@other")
    }
    @Test(expected = IllegalArgumentException::class) fun missingRegistrationRejected() {
        StudentLoginIdentity.email("Ali", "STU", "ANS")
    }
    @Test(expected = IllegalArgumentException::class) fun nonLatinNameRequestsEnglishFirstName() {
        StudentLoginIdentity.email("علی احمد", "102", "ANS")
    }
}
