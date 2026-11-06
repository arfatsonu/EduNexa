package pk.edunexa.app.auth

import org.junit.Assert.*
import org.junit.Test

class LoginGeneratorTest {
    @Test fun retryKeepsEmailAndReadablePasswordStable() {
        val first=LoginGenerator.generate("student","school-one","profile-one","STU-1506","Arfat Mushtaq","ANS")
        val second=LoginGenerator.generate("student","school-one","profile-one","STU-1506","Arfat Mushtaq","ANS")
        assertEquals(first,second)
        assertEquals("Arfat1506",first.password)
    }
    @Test fun sameNameAndNumberAcrossDistinctSchoolShortFormsStaySeparate() {
        val first=LoginGenerator.generate("student","one","A","STU-1","Ali","ANS")
        val second=LoginGenerator.generate("student","two","B","STU-1","Ali","ANPS")
        assertNotEquals(first.email,second.email)
    }
    @Test fun admissionAndEmployeeEmailsRemainStableWithinSchool() {
        assertEquals("ali1052@ans.edu",LoginGenerator.generate("student","one","id","STU-1052","Ali Ahmad","ANS").email)
        assertEquals("ali.t008@ans.edu",LoginGenerator.generate("teacher","one","id","T-008","Ali Ahmad","ANS").email)
    }
    @Test(expected=IllegalArgumentException::class) fun cannotGenerateAdminCredentials() {
        LoginGenerator.generate("admin","one","one")
    }
}
