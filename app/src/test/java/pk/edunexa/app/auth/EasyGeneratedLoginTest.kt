package pk.edunexa.app.auth

import org.junit.Assert.*
import org.junit.Test

class EasyGeneratedLoginTest {
    @Test fun usesFirstNameAndRegistrationNumberForStudentsAndTeachers() {
        assertEquals("Arfat1506",LoginGenerator.generate("student","school","s","STU-1506","Arfat Mushtaq","ANS").password)
        assertEquals("Usman001",LoginGenerator.generate("student","school","s","STU-001","Usman Qamar","ANS").password)
        assertEquals("Safa1234",LoginGenerator.generate("teacher","school","t","EMP-1234","Safa Waris","ANS").password)
    }
    @Test fun teacherSerialUsesReadablePasswordAndStableEmail() {
        val id = pk.edunexa.app.students.EnrollmentPolicy.employee(10089)
        assertEquals("T-10089", id)
        val login = LoginGenerator.generate("teacher", "school", "new-teacher", id, "Arfat Mushtaq", "ANS")
        assertEquals("arfat.t10089@ans.edu", login.email)
        assertEquals("Arfat10089", login.password)
        assertNotEquals(LoginGenerator.generate("teacher", "school", "next-teacher",
            pk.edunexa.app.students.EnrollmentPolicy.employee(10090), "Arfat Mushtaq", "ANS").email, login.email)
    }
    @Test fun preservesEmailIdentityAndMeetsFirebaseMinimumLength() {
        val login=LoginGenerator.generate("student","school","s","STU-1","A","ANS")
        assertEquals("a1@ans.edu",login.email)
        assertTrue(login.password.length>=6)
        assertTrue(LoginGenerator.generate("teacher","school","t","EMP-2","A","ANS").password.length>=6)
    }
}
