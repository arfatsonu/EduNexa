
package pk.edunexa.app.auth

import org.junit.Assert.*
import org.junit.Test

class CampusLoginIdentityTest {
    @Test fun sameRegistrationAcrossSchoolsGetsDifferentAccounts() {
        val first = LoginGenerator.generate("student", "school-a", "student-a", "STU-001", "Ali Raza", "ANS")
        val second = LoginGenerator.generate("student", "school-b", "student-b", "STU-001", "Ali Raza", "ANPS")
        assertNotEquals(first.email, second.email)
        assertEquals("Ali001", first.password)
        assertEquals(first.password, second.password)
    }
    @Test fun schoolIdsCannotCollideAfterPunctuationNormalization() {
        assertNotEquals(CampusLoginIdentity.email("teacher", "school/a", "t001"),
            CampusLoginIdentity.email("teacher", "schoola", "t001"))
    }
    @Test fun longIdentifiersFitEmailLocalPartLimit() {
        val email = CampusLoginIdentity.email("student", "school", "a".repeat(200))
        assertTrue(email.substringBefore("@").length <= 64)
        assertTrue(email.endsWith("@student.a2campus.app"))
    }
}
