package pk.edunexa.app.academic

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import pk.edunexa.app.teachers.TeacherDirectoryRepository
import java.util.Locale
import java.util.UUID

class SubjectRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val directory = TeacherDirectoryRepository(db)
    fun load(schoolId: String, onResult: (Result<SubjectData>) -> Unit) {
        db.collection("schools").document(schoolId).collection("academicConfig").document("current").get(Source.SERVER)
            .addOnSuccessListener { doc -> onResult(runCatching {
                check(doc.exists()) { "Academic settings are unavailable." }
                val config = doc.data.orEmpty()
                SubjectData(subjects(config), maps(config["classes"]).map { CurriculumScope(str(it, "id"), str(it, "name"), it["isActive"] == true) },
                    maps(config["sessions"]).map { CurriculumScope(str(it, "id"), str(it, "name"), true, str(it, "status"), it["id"] == pk.edunexa.app.academic.AcademicSessionPolicy.current(config)["id"]) }, maps(config["classSubjectAssignments"]))
            }) }.addOnFailureListener { onResult(Result.failure(it)) }
    }
    fun save(schoolId: String, subject: SchoolSubject, onResult: (Result<Unit>) -> Unit) {
        directory.updateAcademicConfig(schoolId, onResult) { config, _, _ ->
            SubjectPolicy.validate(subject, subjects(config))
            if (!subject.isActive) {
                val current = currentId(config)
                require(maps(config["teacherAssignments"]).none { it["subjectId"] == subject.id && it["academicSession"] == current }) { "Current teacher assignments are linked. Remove the assignment first." }
            }
            val all = maps(config["subjects"])
            val old = all.firstOrNull { it["id"] == subject.id }.orEmpty()
            config["subjects"] = all.filterNot { it["id"] == subject.id } + (old + mapOf("id" to subject.id, "name" to subject.name.trim(), "code" to subject.code.trim().uppercase(Locale.US), "isActive" to subject.isActive, "displayOrder" to subject.displayOrder))
        }
    }
    fun assign(schoolId: String, classId: String, sessionId: String, subjectId: String, enabled: Boolean, onResult: (Result<Unit>) -> Unit) {
        val newId = UUID.randomUUID().toString()
        directory.updateAcademicConfig(schoolId, onResult) { config, _, _ ->
            validateScope(config, classId, sessionId)
            val subject = subjects(config).firstOrNull { it.id == subjectId }
            require(subject != null) { "The subject was not found. Refresh and try again." }
            require(!enabled || subject.isActive) { "Activate the subject first." }
            if (!enabled) require(maps(config["teacherAssignments"]).none { it["classId"] == classId && it["subjectId"] == subjectId && it["academicSession"] == sessionId }) { "A teacher is assigned to this subject. Remove the assignment first." }
            config["classSubjectAssignments"] = SubjectPolicy.assign(maps(config["classSubjectAssignments"]), classId, sessionId, subjectId, enabled, newId)
        }
    }
    fun assignAll(schoolId: String, classId: String, sessionId: String, onResult: (Result<Unit>) -> Unit) {
        directory.updateAcademicConfig(schoolId, onResult) { config, _, _ ->
            validateScope(config, classId, sessionId)
            var all = maps(config["classSubjectAssignments"])
            subjects(config).filter { it.isActive }.forEach { all = SubjectPolicy.assign(all, classId, sessionId, it.id, true, UUID.randomUUID().toString()) }
            config["classSubjectAssignments"] = all
        }
    }
    fun saveAssignments(schoolId: String, classId: String, sessionId: String, expected: Set<String>, selected: Set<String>, onResult: (Result<Unit>) -> Unit) {
        directory.updateAcademicConfig(schoolId, onResult) { config, _, _ ->
            validateScope(config, classId, sessionId)
            val all = maps(config["classSubjectAssignments"])
            val saved = all.filter { it["classId"] == classId && it["academicSession"] == sessionId }.mapNotNull { it["subjectId"] as? String }.toSet()
            require(saved == expected) { "Assignments changed on another device. Refresh and review before saving." }
            require((selected - saved).all { id -> subjects(config).any { it.id == id && it.isActive } }) { "An added subject is inactive. Refresh and review." }
            val removed = saved - selected
            require(maps(config["teacherAssignments"]).none { it["classId"] == classId && it["academicSession"] == sessionId && it["subjectId"] in removed }) { "Remove the linked teacher assignment before unassigning that subject." }
            var next = all
            (saved + selected).forEach { id -> next = SubjectPolicy.assign(next, classId, sessionId, id, id in selected, UUID.randomUUID().toString()) }
            config["classSubjectAssignments"] = next
        }
    }
    private fun validateScope(config: Map<String, Any?>, classId: String, sessionId: String) {
        require(maps(config["classes"]).any { it["id"] == classId && it["isActive"] == true }) { "Select an active class." }
        require(maps(config["sessions"]).any { it["id"] == sessionId && it["status"] in setOf("Active", "Upcoming") }) { "Select an Active or Upcoming session. Closed sessions are read-only." }
    }
    private fun currentId(config: Map<String, Any?>) = pk.edunexa.app.academic.AcademicSessionPolicy.current(config)["id"]
    private fun subjects(config: Map<String, Any?>) = maps(config["subjects"]).map { SchoolSubject(str(it, "id"), str(it, "name"), str(it, "code"), it["isActive"] == true, (it["displayOrder"] as? Number)?.toInt() ?: 0) }.sortedWith(compareBy({ it.displayOrder }, { it.name }))
    private fun str(item: Map<String, Any?>, key: String) = item[key]?.toString().orEmpty()
    private fun maps(value: Any?): List<Map<String, Any?>> = (value as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { entry -> entry.key.toString() to entry.value } }.orEmpty()
}

