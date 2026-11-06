package pk.edunexa.app.auth

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.teachers.TeacherDeletionPolicy

class TeacherShortLoginTest {
    @Test fun firstNameEmployeeNumberAndShortSchoolReplaceLegacyDigest() {
        val login = LoginGenerator.generate("teacher", "school-001", "teacher", "T-001", "Haba Ahmad", "ANS")
        assertEquals("haba.t001@ans.edu", login.email)
        assertEquals("Haba001", login.password)
        assertEquals(login, LoginGenerator.generate("teacher", "school-001", "teacher", "T-001", "Haba Ahmad", "ans"))
    }
    @Test fun studentAndTeacherWithSameNameAndNumberCannotCollide() {
        assertNotEquals(LoginGenerator.generate("teacher", "school", "t", "T-008", "Ali", "ANS").email,
            LoginGenerator.generate("student", "school", "s", "STU-008", "Ali", "ANS").email)
    }
    @Test(expected = IllegalArgumentException::class) fun schoolShortFormIsRequiredForTeachersToo() {
        LoginGenerator.generate("teacher", "school", "t", "T-001", "Haba")
    }
    @Test fun deletedAccountReservationNeverAddsLongProfileDigits() {
        assertEquals("haba.t001.3@ans.edu", TeacherDeletionPolicy.availableLoginEmail("haba.t001@ans.edu",
            "very-long-profile-id", setOf("HABA.T001@ANS.EDU", "haba.t001.2@ans.edu")))
    }
}
