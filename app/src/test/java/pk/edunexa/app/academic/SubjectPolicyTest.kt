package pk.edunexa.app.academic

import org.junit.Assert.*
import org.junit.Test

class SubjectPolicyTest {
    private fun assignment(id: String, cls: String, session: String, subject: String) = mapOf<String, Any?>("id" to id, "classId" to cls, "academicSession" to session, "subjectId" to subject)
    @Test fun assignmentPreservesOtherClassesAndSessions() {
        val otherClass = assignment("other", "7", "now", "math")
        val historical = assignment("past", "6", "old", "math")
        val result = SubjectPolicy.assign(listOf(otherClass, historical), "6", "now", "math", true, "new")
        assertEquals(listOf(otherClass, historical, assignment("new", "6", "now", "math")), result)
    }
    @Test fun repeatedAssignmentPreservesStableIdAndRemovesDuplicates() {
        val original = assignment("stable", "6", "now", "math") + ("extra" to "preserved")
        val duplicate = assignment("duplicate", "6", "now", "math")
        assertEquals(listOf(original), SubjectPolicy.assign(listOf(original, duplicate), "6", "now", "math", true, "unused"))
    }
    @Test fun unassignOnlyAffectsSelectedScope() {
        val historical = assignment("past", "6", "old", "math")
        val current = assignment("now", "6", "now", "math")
        assertEquals(listOf(historical), SubjectPolicy.assign(listOf(historical, current), "6", "now", "math", false, "unused"))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateNameRejected() {
        SubjectPolicy.validate(SchoolSubject(id = "new", name = " math ", code = "M2"), listOf(SchoolSubject(id = "old", name = "Math", code = "M1")))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateCodeRejected() {
        SubjectPolicy.validate(SchoolSubject(id = "new", name = "Science", code = " mat "), listOf(SchoolSubject(id = "old", name = "Math", code = "MAT")))
    }
    @Test fun sameSubjectCanBeEditedWithoutChangingIdentity() {
        val old = SchoolSubject(id = "stable", name = "Math", code = "MAT")
        SubjectPolicy.validate(old.copy(name = "Mathematics"), listOf(old))
    }
}
