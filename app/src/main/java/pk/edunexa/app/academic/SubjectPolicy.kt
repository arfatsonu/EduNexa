package pk.edunexa.app.academic

import java.util.UUID

data class SchoolSubject(val id: String = UUID.randomUUID().toString(), val name: String = "", val code: String = "", val isActive: Boolean = true, val displayOrder: Int = 0)
data class CurriculumScope(val id: String, val name: String, val active: Boolean, val status: String = "", val current: Boolean = false)
data class SubjectData(val subjects: List<SchoolSubject>, val classes: List<CurriculumScope>, val sessions: List<CurriculumScope>, val assignments: List<Map<String, Any?>>) {
    fun assigned(classId: String, sessionId: String) = assignments.filter { it["classId"] == classId && it["academicSession"] == sessionId }.mapNotNull { it["subjectId"] as? String }.toSet()
}

object SubjectPolicy {
    fun validate(subject: SchoolSubject, others: List<SchoolSubject>) {
        require(subject.name.trim().isNotBlank()) { "Subject name is required." }
        require(others.none { it.id != subject.id && it.name.trim().equals(subject.name.trim(), true) }) { "This subject name already exists." }
        require(subject.code.trim().length in 1..8) { "Subject code must contain 1 to 8 characters." }
        require(others.none { it.id != subject.id && it.code.trim().equals(subject.code.trim(), true) }) { "This subject code already exists." }
    }
    fun assign(existing: List<Map<String, Any?>>, classId: String, sessionId: String, subjectId: String, enabled: Boolean, newId: String): List<Map<String, Any?>> {
        val matches: (Map<String, Any?>) -> Boolean = { it["classId"] == classId && it["academicSession"] == sessionId && it["subjectId"] == subjectId }
        val old = existing.firstOrNull(matches)
        val remainder = existing.filterNot(matches)
        return if (!enabled) remainder else remainder + (old ?: mapOf("id" to newId, "classId" to classId, "subjectId" to subjectId, "academicSession" to sessionId))
    }
}

