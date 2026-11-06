package pk.edunexa.app.academic

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FieldValue
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks
import pk.edunexa.app.teachers.TeacherDirectoryRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

data class AcademicEntry(
    val id: String = UUID.randomUUID().toString(), val name: String = "",
    val wing: String = "", val classId: String = "", val isActive: Boolean = true,
    val displayOrder: Int = 1, val status: String = "Upcoming", val isCurrent: Boolean = false,
    val startDate: String = "", val endDate: String = ""
)
data class AcademicSetupData(val classes: List<AcademicEntry>, val sections: List<AcademicEntry>, val sessions: List<AcademicEntry>) {
    val currentSession: AcademicEntry? get() = sessions.firstOrNull { it.isCurrent }
    fun className(id: String) = classes.firstOrNull { it.id == id }?.name ?: id
}

object AcademicPolicy {
    fun validateSectionDeletion(section: Map<String, Any?>, students: List<Map<String, Any?>>, teachers: List<Map<String, Any?>>, assignments: List<Map<String, Any?>>, sessionId: String) {
        require(section.isNotEmpty()) { "The section was not found. Refresh and try again." }
        require(section["isActive"] == false) { "Only inactive sections can be deleted." }
        val id = section["id"]
        require(students.none { it["sectionId"] == id && it["status"] in setOf("Active", "On Leave") }) { "Active students are linked. Move them to another section first." }
        require(teachers.none { it["inchargeSectionId"] == id && it["status"] == "Active" && (it["inchargeAcademicSession"].toString().let { session -> session == sessionId || session == "null" || session.isBlank() }) }) { "A current Class Incharge is linked. Remove the responsibility first." }
        require(assignments.none { it["sectionId"] == id && it["academicSession"] == sessionId }) { "Current teacher assignments are linked. Remove them first." }
    }

    fun validateName(name: String, id: String, others: List<AcademicEntry>) {
        require(name.trim().isNotEmpty()) { "Name is required." }
        require(others.none { it.id != id && it.name.trim().equals(name.trim(), true) }) { "This name already exists." }
    }
    fun validateDates(start: String, end: String) {
        fun valid(value: String): Boolean {
            if (value.isBlank()) return true
            if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            return runCatching { format.format(format.parse(value)!!) == value }.getOrDefault(false)
        }
        require(valid(start) && valid(end)) { "Enter valid dates in YYYY-MM-DD format." }
        require(start.isBlank() || end.isBlank() || start <= end) { "End date cannot be before start date." }
    }
    fun selectCurrent(sessions: List<AcademicEntry>, id: String): List<AcademicEntry> {
        val target = sessions.firstOrNull { it.id == id } ?: throw IllegalArgumentException("The academic session was not found.")
        require(target.status != "Closed") { "A Closed session cannot be current. Change its status first." }
        return sessions.map { it.copy(isCurrent = it.id == id, status = if (it.id == id) "Active" else it.status) }
    }
}

class AcademicSetupRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val directory = TeacherDirectoryRepository(db)
    fun load(schoolId: String, onResult: (Result<AcademicSetupData>) -> Unit) {
        db.collection("schools").document(schoolId).collection("academicConfig").document("current").get(Source.SERVER)
            .addOnSuccessListener { doc -> onResult(runCatching {
                check(doc.exists()) { "Academic settings are unavailable." }
                AcademicSetupData(entries(doc.get("classes")), entries(doc.get("sections")), entries(doc.get("sessions")))
            }) }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun save(schoolId: String, type: String, entry: AcademicEntry, onResult: (Result<Unit>) -> Unit) {
        directory.updateAcademicConfig(schoolId, onResult) { config, students, teachers ->
            check(type in setOf("classes", "sections", "sessions"))
            val raw = maps(config[type]).toMutableList()
            val old = raw.firstOrNull { it["id"] == entry.id }.orEmpty()
            val peers = if (type == "sections") entries(config[type]).filter { it.classId == entry.classId } else entries(config[type])
            AcademicPolicy.validateName(entry.name, entry.id, peers)
            require(entry.displayOrder in 0..9999) { "Display order must be between 0 and 9999." }
            if (type == "sections") {
                val parent = maps(config["classes"]).firstOrNull { it["id"] == entry.classId }
                require(parent != null) { "Select a valid parent class." }
                require(!entry.isActive || parent["isActive"] == true) { "An active section requires an active parent class." }
                require(old.isEmpty() || old["classId"] == entry.classId) { "The parent class of an existing section cannot change." }
            }
            if (type in setOf("classes", "sections") && !entry.isActive) {
                val field = if (type == "classes") "classId" else "sectionId"
                require(students.none { it[field] == entry.id && (it["status"] == "Active" || it["status"] == "On Leave") }) {
                    "Active students are linked. Move them to an active class and section first."
                }
                val inchargeField = if (type == "classes") "inchargeClassId" else "inchargeSectionId"
                require(teachers.none { it[inchargeField] == entry.id && it["status"] == "Active" }) { "A Class Incharge is linked. Remove the responsibility first." }
                val currentId = maps(config["sessions"]).firstOrNull { it["isCurrent"] == true }?.get("id")
                require(maps(config["teacherAssignments"]).none { it[field] == entry.id && it["academicSession"] == currentId }) {
                    "Current teacher assignments are linked. Remove them first."
                }
            }
            val payload: Map<String, Any?> = when (type) {
                "classes" -> mapOf("id" to entry.id, "name" to entry.name.trim(), "wing" to entry.wing.trim(), "isActive" to entry.isActive, "displayOrder" to entry.displayOrder)
                "sections" -> mapOf("id" to entry.id, "name" to entry.name.trim(), "classId" to entry.classId, "isActive" to entry.isActive, "displayOrder" to entry.displayOrder)
                else -> {
                    AcademicPolicy.validateDates(entry.startDate, entry.endDate)
                    require(entry.status in setOf("Active", "Closed", "Upcoming", "Inactive"))
                    require(old["isCurrent"] != true || entry.status == "Active") { "The current session must remain Active. Set another session as current first." }
                    mapOf("id" to entry.id, "name" to entry.name.trim(), "status" to entry.status,
                        "startDate" to entry.startDate, "endDate" to entry.endDate, "isCurrent" to (old["isCurrent"] == true))
                }
            }
            raw.removeAll { it["id"] == entry.id }
            raw.add(old + payload)
            config[type] = raw
        }
    }

    fun deleteSection(schoolId: String, sectionId: String, onResult: (Result<Unit>) -> Unit) {
        deleteSectionScoped(schoolId, sectionId, 0, onResult)
    }

    private class SectionLinksChanged : IllegalStateException("Section links changed. Refresh and try again.")

    private fun deleteSectionScoped(schoolId: String, sectionId: String, attempt: Int, onResult: (Result<Unit>) -> Unit) {
        val caller = FirebaseAuth.getInstance().currentUser?.uid
        if (caller == null || sectionId.isBlank()) {
            onResult(Result.failure(IllegalStateException("Admin login and a section are required."))); return
        }
        val root = db.collection("schools").document(schoolId)
        val configRef = root.collection("academicConfig").document("current")
        // Capture the shared academic revision before querying links. Enrollment and
        // responsibility changes serialize on this same config document.
        configRef.get(Source.SERVER).addOnSuccessListener { baseline ->
            val studentsTask = root.collection("students").whereEqualTo("sectionId", sectionId)
                .whereEqualTo("status", "Active").limit(1).get(Source.SERVER)
            val leaveTask = root.collection("students").whereEqualTo("sectionId", sectionId)
                .whereEqualTo("status", "On Leave").limit(1).get(Source.SERVER)
            val teachersTask = root.collection("teachers").whereEqualTo("inchargeSectionId", sectionId)
                .whereEqualTo("status", "Active").get(Source.SERVER)
            Tasks.whenAllComplete(studentsTask, leaveTask, teachersTask).addOnCompleteListener {
                val failure = listOf(studentsTask, leaveTask, teachersTask).firstOrNull { !it.isSuccessful }?.exception
                if (failure != null) { onResult(Result.failure(failure)); return@addOnCompleteListener }
                db.runTransaction { tx ->
                    val admin = tx.get(db.collection("users").document(caller))
                    check(admin.getString("role") == "admin" && admin.getBoolean("active") == true && admin.getBoolean("portalDisabled") != true && admin.getString("schoolId") == schoolId) { "An active school Admin account is required." }
                    val latest = tx.get(configRef)
                    check(latest.exists()) { "Academic settings are unavailable." }
                    if (latest.data != baseline.data) throw SectionLinksChanged()
                    val students = (studentsTask.result.documents + leaveTask.result.documents).map { known ->
                        val current = tx.get(known.reference)
                        // If a sampled blocker was removed, re-query before allowing deletion:
                        // another enrolled student may still belong to this section.
                        if (!current.exists() || current.getString("sectionId") != sectionId || current.getString("status") !in setOf("Active", "On Leave")) throw SectionLinksChanged()
                        current.data.orEmpty()
                    }
                    val teachers = teachersTask.result.documents.mapNotNull { tx.get(it.reference).takeIf { doc -> doc.exists() }?.data }
                    val config = latest.data.orEmpty()
                    val sections = maps(config["sections"])
                    val section = sections.firstOrNull { it["id"] == sectionId }.orEmpty()
                    val currentId = maps(config["sessions"]).firstOrNull { it["isCurrent"] == true }?.get("id")?.toString().orEmpty()
                    AcademicPolicy.validateSectionDeletion(section, students, teachers, maps(config["teacherAssignments"]), currentId)
                    // An unused inactive section changes only the selectable config;
                    // teacher scopes and historical records retain their stable IDs.
                    tx.update(configRef, mapOf(
                        "sections" to sections.filterNot { it["id"] == sectionId },
                        "archivedSections" to (maps(config["archivedSections"]).filterNot { it["id"] == sectionId } + section),
                        "academicUpdatedAt" to FieldValue.serverTimestamp()
                    ))
                    Unit
                }.addOnSuccessListener { onResult(Result.success(Unit)) }.addOnFailureListener { error ->
                    val linksChanged = generateSequence<Throwable>(error) { it.cause }.any { it is SectionLinksChanged }
                    if (linksChanged && attempt < 2) deleteSectionScoped(schoolId, sectionId, attempt + 1, onResult)
                    else onResult(Result.failure(error))
                }
            }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun setCurrent(schoolId: String, sessionId: String, onResult: (Result<Unit>) -> Unit) {
        directory.updateAcademicConfig(schoolId, onResult) { config, _, _ ->
            val selected = AcademicPolicy.selectCurrent(entries(config["sessions"]), sessionId)
            config["sessions"] = maps(config["sessions"]).map { raw ->
                val item = selected.first { it.id == raw["id"] }
                raw + mapOf("isCurrent" to item.isCurrent, "status" to item.status)
            }
        }
    }

    private fun entries(value: Any?): List<AcademicEntry> = maps(value).map {
        fun str(key: String) = it[key]?.toString().orEmpty()
        AcademicEntry(id = str("id"), name = str("name"), wing = str("wing"), classId = str("classId"),
            isActive = it["isActive"] as? Boolean ?: false, displayOrder = (it["displayOrder"] as? Number)?.toInt() ?: 0,
            status = str("status"), isCurrent = it["isCurrent"] == true, startDate = str("startDate"), endDate = str("endDate"))
    }.sortedWith(compareBy({ it.displayOrder }, { it.name }))
    private fun maps(value: Any?): List<Map<String, Any?>> = (value as? List<*>)?.mapNotNull { entry ->
        (entry as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
    }.orEmpty()
}


