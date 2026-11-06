package pk.edunexa.app.exams

import org.junit.Assert.*
import org.junit.Test

class ExamPolicyTest {
    @Test(expected = IllegalStateException::class) fun adminCannotWriteTestsOrMarks() { ExamPolicy.requireTeacher(pk.edunexa.app.auth.PortalRole.ADMIN) }
    @Test(expected = IllegalStateException::class) fun studentCannotWriteTestsOrMarks() { ExamPolicy.requireTeacher(pk.edunexa.app.auth.PortalRole.STUDENT) }
    @Test fun teacherCanManageEveryAssessmentType() { ExamPolicy.requireTeacher(pk.edunexa.app.auth.PortalRole.TEACHER); assertTrue(ExamPolicy.types.containsAll(listOf("1st Term", "2nd Term", "Final Term", "Class Test", "Weekly Test", "Monthly Test", "Board Test Session"))) }
    @Test fun preboardStagesFollowRequestedSequence() { assertEquals(listOf("Chapter-wise Test", "One-third Book Test", "Half Book Test", "Full Book Test"), ExamPolicy.stages) }

    @Test(expected = IllegalArgumentException::class) fun marksAboveTotalRejected() { ExamPolicy.validateMarks("Present", 51.0, 50.0) }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteMarksRejected() { ExamPolicy.validateMarks("Present", Double.NaN, 50.0) }
    @Test(expected = IllegalArgumentException::class) fun absentMarksMustBeBlank() { ExamPolicy.validateMarks("Absent", 0.0, 50.0) }
    @Test fun zeroIsAValidEnteredMark() { assertTrue(ExamPolicy.complete(listOf(mapOf("studentId" to "one", "status" to "Present", "marksObtained" to 0.0)), setOf("one"), 50.0)) }
    @Test fun missingStudentCannotPublish() { assertFalse(ExamPolicy.complete(listOf(mapOf("studentId" to "one", "status" to "Present", "marksObtained" to 40.0)), setOf("one", "two"), 50.0)) }
    @Test fun pendingIsNotZero() { assertFalse(ExamPolicy.complete(listOf(mapOf("studentId" to "one", "status" to "Present", "marksObtained" to null)), setOf("one"), 50.0)) }
    @Test fun absentAndExemptCountAsCompleteStatuses() { assertTrue(ExamPolicy.complete(listOf(mapOf("studentId" to "one", "status" to "Absent"), mapOf("studentId" to "two", "status" to "Exempt")), setOf("one", "two"), 50.0)) }
    @Test fun emptyRosterCannotPublish() { assertFalse(ExamPolicy.complete(emptyList(), emptySet(), 50.0)) }
    @Test fun stableMarkIdentity() { assertEquals("test__student", ExamPolicy.markId("test", "student")); assertNotEquals(ExamPolicy.markId("test", "one"), ExamPolicy.markId("test", "two")) }
}
