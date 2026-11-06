package pk.edunexa.app.diary

import org.junit.Assert.*
import org.junit.Test

class TeacherDiaryPolicyTest {
    private val config = mapOf<String, Any?>("sessions" to listOf(mapOf("id" to "current", "isCurrent" to true)), "teacherAssignments" to listOf(mapOf("teacherId" to "one", "classId" to "6", "sectionId" to "boys", "subjectId" to "math", "academicSession" to "current")))
    private val own = DiaryItem(raw = mapOf("academicSessionId" to "current", "classId" to "6", "sectionId" to "boys", "subjectId" to "math"))
    @Test fun assignedTeacherCanWrite() { assertTrue(TeacherDiaryPolicy.canWrite(config, own, "one")) }
    @Test fun differentTeacherCannotWrite() { assertFalse(TeacherDiaryPolicy.canWrite(config, own, "two")) }
    @Test fun differentSectionCannotWrite() { assertFalse(TeacherDiaryPolicy.canWrite(config, own.copy(raw = own.raw + ("sectionId" to "girls")), "one")) }
    @Test fun differentSubjectCannotWrite() { assertFalse(TeacherDiaryPolicy.canWrite(config, own.copy(raw = own.raw + ("subjectId" to "science")), "one")) }
    @Test fun previousSessionCannotWrite() { assertFalse(TeacherDiaryPolicy.canWrite(config, own.copy(raw = own.raw + ("academicSessionId" to "previous")), "one")) }
}
