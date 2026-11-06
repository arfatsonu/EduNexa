package pk.edunexa.app.teachers

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.util.Locale
import java.util.UUID

data class TeacherItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val employeeId: String = "",
    val phone: String = "",
    val email: String = "",
    val qualification: String = "",
    val joiningDate: String = "",
    val status: String = "Active",
    val portalEmail: String = "",
    val authUid: String = "",
    val inchargeClassId: String = "",
    val inchargeSectionId: String = "",
    val inchargeAcademicSession: String = "",
    val profilePhoto: String? = null
)

data class AcademicOption(val id: String, val name: String, val parentId: String = "")
data class TeacherAssignment(
    val id: String = UUID.randomUUID().toString(),
    val teacherId: String,
    val classId: String,
    val sectionId: String,
    val subjectId: String,
    val academicSession: String
)
data class TeacherDirectoryData(
    val teachers: List<TeacherItem>,
    val classes: List<AcademicOption>,
    val sections: List<AcademicOption>,
    val subjects: List<AcademicOption>,
    val sessions: List<AcademicOption>,
    val assignments: List<TeacherAssignment>,
    val curriculum: List<Map<String, Any?>>,
    val reservedLoginEmails: Set<String> = emptySet(),
    val schoolShortName: String = ""
) {
    fun subjectsFor(classId: String, sessionId: String): List<AcademicOption> {
        val ids = curriculum.filter {
            it["classId"] == classId && it["academicSession"] == sessionId
        }.map { it["subjectId"] }.toSet()
        return subjects.filter { it.id in ids }
    }
    fun sessionLabel(id: String) = sessions.firstOrNull { it.id == id }?.name ?: "Session unavailable"
    fun classLabel(id: String) = classes.firstOrNull { it.id == id }?.name ?: "Class unavailable"
    fun sectionLabel(id: String) = sections.firstOrNull { it.id == id }?.name ?: "Section unavailable"
    fun subjectLabel(id: String) = subjects.firstOrNull { it.id == id }?.name ?: "Subject unavailable"
}

class TeacherDirectoryRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun school(id: String) = db.collection("schools").document(id)
    fun load(schoolId: String, onResult: (Result<TeacherDirectoryData>) -> Unit) {
        val identity = school(schoolId).get(Source.SERVER)
        val teachers = school(schoolId).collection("teachers").get(Source.SERVER)
        val config = school(schoolId).collection("academicConfig").document("current").get(Source.SERVER)
        val users = db.collection("users").whereEqualTo("schoolId", schoolId).get(Source.SERVER)
        Tasks.whenAllComplete(teachers, config, users, identity).addOnCompleteListener {
            val failure = listOf(teachers, config, users, identity).firstOrNull { !it.isSuccessful }?.exception
            if (failure != null) { onResult(Result.failure(failure)); return@addOnCompleteListener }
            onResult(runCatching {
                check(config.result.exists()) { "Academic settings are unavailable." }
                val raw = config.result.data.orEmpty()
                TeacherDirectoryData(
                    teachers.result.documents.filter { it.getBoolean("isDeleted") != true }.map { doc ->
                        val item = teacher(doc)
                        val user = users.result.documents.firstOrNull {
                            it.getString("role") == "teacher" && it.getString("profileId") == doc.id
                        }
                        item.copy(authUid = item.authUid.ifBlank { user?.id.orEmpty() },
                            portalEmail = user?.getString("email")?.ifBlank { item.portalEmail } ?: item.portalEmail)
                    }.sortedBy { it.name.lowercase(Locale.US) },
                    options(raw, "classes"), options(raw, "sections", "classId"),
                    options(raw, "subjects"), options(raw, "sessions", onlyActive = false),
                    assignments(raw), maps(raw["classSubjectAssignments"]),
                    users.result.documents.mapNotNull { it.getString("email")?.trim()?.lowercase(Locale.US) }.toSet(),
                    identity.result.getString("settings.schoolShortName").orEmpty()
                )
            })
        }
    }

    fun saveTeacher(schoolId: String, item: TeacherItem, onResult: (Result<Unit>) -> Unit) {
        val caller = FirebaseAuth.getInstance().currentUser?.uid
        if (caller == null) { onResult(Result.failure(IllegalStateException("Admin login required."))); return }
        val ref = school(schoolId).collection("teachers").document(item.id)
        db.runTransaction { tx ->
            val admin = tx.get(db.collection("users").document(caller))
            check(admin.getString("role") == "admin" && admin.getBoolean("active") == true && admin.getBoolean("portalDisabled") != true && admin.getString("schoolId") == schoolId) { "Active school Admin required." }
            val previous = tx.get(ref)
            check(previous.getBoolean("isDeleted") != true) { "This teacher has been deleted. Add a new teacher profile." }
            val sameScope = previous.exists() && previous.getString("employeeId") == item.employeeId.trim() &&
                previous.getString("name") == item.name.trim() && previous.getString("status") == item.status &&
                previous.getString("inchargeClassId").orEmpty() == item.inchargeClassId &&
                previous.getString("inchargeSectionId").orEmpty() == item.inchargeSectionId &&
                previous.getString("inchargeAcademicSession").orEmpty() == item.inchargeAcademicSession
            if (!sameScope || previous.get("academicDirectory") !is Map<*, *>) return@runTransaction false
            val updates = mapOf("phone" to item.phone.trim(), "email" to item.email.trim(), "qualification" to item.qualification.trim(), "joiningDate" to item.joiningDate.trim(), "profilePhoto" to item.profilePhoto)
            val scoped = (previous.get("academicDirectory") as Map<*, *>).entries.associate { it.key.toString() to it.value }
            val roster = maps(scoped["teacherRoster"]).map { if (it["id"] == item.id) it + updates.filterKeys { key -> key != "profilePhoto" } else it }
            tx.update(ref, updates + mapOf("academicDirectory.teacherRoster" to roster, "updatedAt" to FieldValue.serverTimestamp()))
            true
        }.addOnSuccessListener { saved ->
            if (saved) onResult(Result.success(Unit)) else saveTeacherWithDirectory(schoolId, item, onResult)
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun importTeacher(schoolId: String, item: TeacherItem, onResult: (Result<Unit>) -> Unit) =
        saveTeacherWithDirectory(schoolId, item, onResult, imported = true)

    private fun saveTeacherWithDirectory(schoolId: String, item: TeacherItem, onResult: (Result<Unit>) -> Unit, imported: Boolean = false) {
        if (item.name.isBlank() || item.employeeId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Name and Employee ID are required."))); return
        }
        mutate(schoolId, onResult, teacherWriteId = item.id) { teachers, config, _ ->
            require(item.employeeId.trim().matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,39}"))) { "Employee ID must contain 1–40 letters, digits, underscores or hyphens." }
            check(teachers.none { it["id"] != item.id && str(it, "employeeId").equals(item.employeeId.trim(), true) }) {
                "This Employee ID is already assigned."
            }
            if (item.inchargeClassId.isNotBlank()) {
                val activeSession = maps(config["sessions"]).firstOrNull { it["isCurrent"] == true }
                    ?: maps(config["sessions"]).firstOrNull { it["status"] == "Active" }
                check(item.inchargeAcademicSession == activeSession?.get("id")) { "Assign Class Incharge responsibility for the active session." }
                check(validScope(config, item.inchargeClassId, item.inchargeSectionId, item.inchargeAcademicSession)) {
                    "Select a valid Class Incharge class, section and session."
                }
                check(teachers.none {
                    it["id"] != item.id && it["status"] == "Active" &&
                        it["inchargeClassId"] == item.inchargeClassId &&
                        it["inchargeSectionId"] == item.inchargeSectionId &&
                        (str(it, "inchargeAcademicSession").isBlank() || it["inchargeAcademicSession"] == item.inchargeAcademicSession)
                }) { "A Class Incharge is already assigned to this class and section. Remove that responsibility first." }
            }
            val previous = teachers.firstOrNull { it["id"] == item.id }.orEmpty()
            if (imported) {
                check(previous.isEmpty() || pk.edunexa.app.imports.ImportPolicy.key(str(previous, "name")) == pk.edunexa.app.imports.ImportPolicy.key(item.name)) { "This imported phone belongs to another teacher." }
                check(teachers.none { it["id"] != item.id && (pk.edunexa.app.imports.ImportPolicy.phone(str(it, "phone")) == pk.edunexa.app.imports.ImportPolicy.phone(item.phone) || item.email.isNotBlank() && str(it, "email").equals(item.email, true)) }) { "A teacher already uses this phone or contact email." }
                if (previous.isNotEmpty()) return@mutate
            }
            if (item.authUid.isNotBlank()) {
                check(str(previous, "employeeId") == item.employeeId.trim()) { "The Employee ID of a linked login cannot change." }
            }
            val updated = previous + mapOf(
                "id" to item.id, "name" to item.name.trim(), "employeeId" to item.employeeId.trim(),
                "loginUsername" to item.employeeId.trim(), "phone" to item.phone.trim(),
                "email" to item.email.trim(), "qualification" to item.qualification.trim(),
                "joiningDate" to item.joiningDate.trim(), "status" to item.status,
                "inchargeClassId" to item.inchargeClassId, "inchargeSectionId" to item.inchargeSectionId,
                "inchargeAcademicSession" to if (item.inchargeClassId.isBlank()) "" else item.inchargeAcademicSession,
                "profilePhoto" to item.profilePhoto
            )
            teachers.removeAll { it["id"] == item.id }
            teachers.add(updated)
        }
    }

    fun saveAssignment(schoolId: String, assignment: TeacherAssignment, onResult: (Result<Unit>) -> Unit) {
        mutate(schoolId, onResult) { teachers, config, _ ->
            val owner = teachers.firstOrNull { it["id"] == assignment.teacherId }
            check(owner?.get("status") == "Active") { "Select an active teacher." }
            check(validScope(config, assignment.classId, assignment.sectionId, assignment.academicSession)) {
                "The class, section or session is invalid."
            }
            check(maps(config["classSubjectAssignments"]).any {
                it["classId"] == assignment.classId && it["subjectId"] == assignment.subjectId &&
                    it["academicSession"] == assignment.academicSession
            }) { "This subject is not assigned to the class curriculum." }
            check(options(config, "subjects").any { it.id == assignment.subjectId }) { "This subject is inactive." }
            val current = maps(config["teacherAssignments"]).toMutableList()
            check(current.none {
                it["id"] != assignment.id && it["classId"] == assignment.classId &&
                    it["sectionId"] == assignment.sectionId && it["subjectId"] == assignment.subjectId &&
                    it["academicSession"] == assignment.academicSession
            }) { "A teacher is already assigned to this class, section and subject." }
            current.removeAll { it["id"] == assignment.id }
            current.add(mapOf(
                "id" to assignment.id, "teacherId" to assignment.teacherId,
                "teacherName" to str(owner.orEmpty(), "name"), "classId" to assignment.classId,
                "sectionId" to assignment.sectionId, "subjectId" to assignment.subjectId,
                "academicSession" to assignment.academicSession
            ))
            config["teacherAssignments"] = current
        }
    }

    fun removeAssignment(schoolId: String, id: String, onResult: (Result<Unit>) -> Unit) {
        mutate(schoolId, onResult) { _, config, _ ->
            config["teacherAssignments"] = maps(config["teacherAssignments"]).filter { it["id"] != id }
        }
    }

    fun deleteTeacher(schoolId: String, teacherId: String, onResult: (Result<Unit>) -> Unit) {
        val caller = FirebaseAuth.getInstance().currentUser?.uid
        if (caller == null || teacherId.isBlank()) {
            onResult(Result.failure(IllegalStateException("Admin login and a teacher profile are required."))); return
        }
        val root = school(schoolId)
        val profileRef = root.collection("teachers").document(teacherId)
        val configRef = root.collection("academicConfig").document("current")
        db.collection("users").whereEqualTo("schoolId", schoolId).whereEqualTo("profileId", teacherId).get(Source.SERVER)
            .addOnSuccessListener { accounts ->
                db.runTransaction { tx ->
                    val admin = tx.get(db.collection("users").document(caller))
                    check(admin.getString("role") == "admin" && admin.getBoolean("active") == true &&
                        admin.getBoolean("portalDisabled") != true && admin.getString("schoolId") == schoolId) {
                        "An active school Admin account is required."
                    }
                    val profile = tx.get(profileRef)
                    check(profile.exists()) { "The teacher record no longer exists. Refresh the directory." }
                    val config = tx.get(configRef)
                    check(config.exists()) { "Academic settings are unavailable." }
                    val uid = profile.getString("authUid").orEmpty()
                    val refs = (accounts.documents.filter {
                        it.getString("role") == "teacher" && it.getString("profileId") == teacherId
                    }.map { it.reference } + if (uid.isBlank()) emptyList() else listOf(db.collection("users").document(uid)))
                        .distinctBy { it.path }
                    val linked = refs.map { tx.get(it) }.filter { it.exists() }
                    check(linked.size + 2 < 500) { "Too many linked accounts. Review teacher login links." }
                    linked.forEach { user ->
                        check(user.id != caller && user.getString("schoolId") == schoolId &&
                            user.getString("role") == "teacher" && user.getString("profileId") == teacherId) {
                            "Login linkage is inconsistent. Deletion was stopped."
                        }
                    }
                    // Shared config serializes this operation with assignment and incharge edits.
                    val remaining = TeacherDeletionPolicy.remainingAssignments(maps(config.get("teacherAssignments")), teacherId)
                    linked.forEach { user -> tx.update(user.reference, mapOf(
                        "active" to false, "portalDisabled" to true, "updatedAt" to FieldValue.serverTimestamp())) }
                    tx.update(profileRef, mapOf(
                        "isDeleted" to true, "status" to "Inactive", "deletedBy" to caller,
                        "deletedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(),
                        "inchargeClassId" to "", "inchargeSectionId" to "", "inchargeAcademicSession" to "",
                        "academicDirectory" to emptyMap<String, Any>(), "subjectAccessKeys" to emptyList<String>(),
                        "academicDirectoryUpdatedAt" to FieldValue.serverTimestamp()) + pk.edunexa.app.activity.ActivityHistory.patch(profile.data.orEmpty(),mapOf("isDeleted" to true,"status" to "Inactive"),caller,admin.getString("name").orEmpty(),"admin","Teacher removed",profile.getString("name").orEmpty()))
                    tx.update(configRef, mapOf(
                        "teacherAssignments" to remaining,
                        "teacherRegistryIds" to (config.get("teacherRegistryIds") as? List<*>).orEmpty().filter { it != teacherId },
                        "academicUpdatedAt" to FieldValue.serverTimestamp()))
                    Unit
                }.addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun updateAcademicConfig(
        schoolId: String, onResult: (Result<Unit>) -> Unit,
        edit: (MutableMap<String, Any?>, List<Map<String, Any?>>, List<Map<String, Any?>>) -> Unit
    ) {
        mutate(schoolId, onResult) { teachers, config, students -> edit(config, students, teachers) }
    }

    fun saveStudentWithDirectory(schoolId: String, studentId: String, payload: Map<String, Any?>, onResult: (Result<Unit>) -> Unit) {
        val caller = FirebaseAuth.getInstance().currentUser?.uid
        if (caller == null) { onResult(Result.failure(IllegalStateException("Admin login required."))); return }
        fun saveFullDirectory() {
            mutate(schoolId, onResult, studentWrite = studentId to payload) { _, _, students ->
                check(students.none { it["id"] != studentId && str(it, "admissionNo").equals(str(payload, "admissionNo"), true) }) { "This admission number is already assigned." }
                if (str(payload, "rollNo").isNotBlank() && payload["status"] != "Inactive") {
                    check(students.none { it["id"] != studentId && it["isDeleted"] != true && it["status"] != "Inactive" && it["classId"] == payload["classId"] && it["sectionId"] == payload["sectionId"] && it["academicSessionId"] == payload["academicSessionId"] && str(it, "rollNo") == str(payload, "rollNo") }) { "This roll number is already assigned." }
                }
            }
        }
        val ref = school(schoolId).collection("students").document(studentId)
        db.runTransaction { tx ->
            val admin = tx.get(db.collection("users").document(caller))
            check(admin.getString("role") == "admin" && admin.getBoolean("active") == true && admin.getBoolean("portalDisabled") != true && admin.getString("schoolId") == schoolId) { "An active school Admin account is required." }
            val previous = tx.get(ref)
            check(previous.getBoolean("isDeleted") != true) { "This student has been removed from the directory." }
            when {
                !previous.exists() -> 0
                StudentSavePolicy.canSkipDirectory(previous.data.orEmpty(), payload) -> { tx.set(ref, payload + pk.edunexa.app.activity.ActivityHistory.patch(previous.data.orEmpty(),payload,caller,admin.getString("name").orEmpty(),"admin","Student profile updated",payload["fullName"].toString()), SetOptions.merge()); 1 }
                StudentRosterUpdatePolicy.canUpdateExisting(previous.data.orEmpty(), payload) -> 2
                else -> 0
            }
        }.addOnSuccessListener { route ->
            when (route) {
                1 -> onResult(Result.success(Unit))
                2 -> updateExistingStudentRoster(schoolId, studentId, payload) { result ->
                    result.onFailure { onResult(Result.failure(it)) }.onSuccess { updated ->
                        if (updated) onResult(Result.success(Unit)) else saveFullDirectory()
                    }
                }
                else -> saveFullDirectory()
            }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Contact/name/status edits update one roster entry atomically, without reading every student.
    private fun updateExistingStudentRoster(schoolId: String, studentId: String, payload: Map<String, Any?>, onResult: (Result<Boolean>) -> Unit) {
        val caller = FirebaseAuth.getInstance().currentUser?.uid
        if (caller == null) { onResult(Result.failure(IllegalStateException("Admin login required."))); return }
        val root = school(schoolId)
        val directory = root.collection("teachers")
        directory.get(Source.SERVER).addOnSuccessListener { known ->
            db.runTransaction { tx ->
                val admin = tx.get(db.collection("users").document(caller))
                check(admin.getString("role") == "admin" && admin.getBoolean("active") == true && admin.getBoolean("portalDisabled") != true && admin.getString("schoolId") == schoolId) { "Active school Admin required." }
                val studentRef = root.collection("students").document(studentId)
                val previous = tx.get(studentRef)
                check(previous.getBoolean("isDeleted") != true) { "This student has been removed from the directory." }
                if (!previous.exists() || !StudentRosterUpdatePolicy.canUpdateExisting(previous.data.orEmpty(), payload)) return@runTransaction false
                val configDoc = tx.get(root.collection("academicConfig").document("current"))
                check(configDoc.exists()) { "Academic settings are unavailable." }
                val config = configDoc.data.orEmpty()
                val sessionId = maps(config["sessions"]).firstOrNull { it["isCurrent"] == true }?.get("id")?.toString()
                    ?: maps(config["sessions"]).firstOrNull { it["status"] == "Active" }?.get("id")?.toString().orEmpty()
                val ids = (known.documents.map { it.id } + (config["teacherRegistryIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()).distinct()
                check(sessionId.isNotBlank()) { "Set an active academic session." }
                val teachers = ids.map { tx.get(directory.document(it)) }.filter { it.exists() && it.getBoolean("isDeleted") != true }
                if (teachers.any { it.get("academicDirectory") !is Map<*, *> || it.getString("activeAcademicSessionId") != sessionId }) return@runTransaction false
                val student = previous.data.orEmpty() + payload + ("id" to studentId)
                val changes = teachers.mapNotNull { teacher ->
                    val scoped = (teacher.get("academicDirectory") as Map<*, *>).entries.associate { it.key.toString() to it.value }
                    val scopes = maps(config["teacherAssignments"]).filter { it["teacherId"] == teacher.id && it["academicSession"] == sessionId }.map { it["classId"] to it["sectionId"] }.toMutableSet()
                    if (teacher.getString("inchargeAcademicSession").orEmpty() in listOf("", sessionId)) scopes.add(teacher.getString("inchargeClassId") to teacher.getString("inchargeSectionId"))
                    val included = teacher.getString("status") == "Active" && student["academicSessionId"] == sessionId && (student["classId"] to student["sectionId"]) in scopes
                    val oldRoster = maps(scoped["studentRoster"])
                    val nextRoster = StudentRosterUpdatePolicy.update(oldRoster, student, included)
                    if (oldRoster == nextRoster) null else teacher.reference to nextRoster
                }
                check(changes.size + 1 < 500) { "Too many linked teacher records." }
                changes.forEach { (ref, roster) -> tx.update(ref, mapOf("academicDirectory.studentRoster" to roster, "academicDirectoryUpdatedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())) }
                tx.set(studentRef, payload + pk.edunexa.app.activity.ActivityHistory.patch(previous.data.orEmpty(),payload,caller,admin.getString("name").orEmpty(),"admin","Student profile updated",payload["fullName"].toString()), SetOptions.merge())
                true
            }.addOnSuccessListener { onResult(Result.success(it)) }.addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    // A transaction serializes directory/assignment changes against the shared config,
    // preserves unrelated academic data and updates scoped permission projections together.
    private fun mutate(
        schoolId: String, onResult: (Result<Unit>) -> Unit,
        studentWrite: Pair<String, Map<String, Any?>>? = null,
        teacherWriteId: String? = null,
        edit: (MutableList<Map<String, Any?>>, MutableMap<String, Any?>, List<Map<String, Any?>>) -> Unit
    ) {
        val caller = FirebaseAuth.getInstance().currentUser?.uid
        if (caller == null) { onResult(Result.failure(IllegalStateException("Admin login required."))); return }
        val root = school(schoolId)
        val directory = root.collection("teachers")
        val teacherTask = directory.get(Source.SERVER)
        val usersTask = db.collection("users").whereEqualTo("schoolId", schoolId).get(Source.SERVER)
        val studentsTask = root.collection("students").get(Source.SERVER)
        Tasks.whenAllComplete(teacherTask, usersTask, studentsTask).addOnCompleteListener {
            val error = listOf(teacherTask, usersTask, studentsTask).firstOrNull { !it.isSuccessful }?.exception
            if (error != null) { onResult(Result.failure(error)); return@addOnCompleteListener }
            val configRef = root.collection("academicConfig").document("current")
            db.runTransaction { transaction ->
                val admin = transaction.get(db.collection("users").document(caller))
                check(admin.getString("role") == "admin" && admin.getBoolean("active") == true && admin.getString("schoolId") == schoolId) {
                    "An active school Admin account is required."
                }
                val configSnapshot = transaction.get(configRef)
                check(configSnapshot.exists()) { "Academic settings are unavailable." }
                val config: MutableMap<String, Any?> = configSnapshot.data.orEmpty().toMutableMap()
                val registryIds = (config["teacherRegistryIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                val ids = (teacherTask.result.documents.map { doc -> doc.id } + registryIds).distinct()
                val before: List<Map<String, Any?>> = ids.mapNotNull { id ->
                    val doc = transaction.get(directory.document(id))
                    if (doc.exists() && doc.getBoolean("isDeleted") != true) doc.data.orEmpty() + ("id" to id) else null
                }
                teacherWriteId?.let { id ->
                    val previous = transaction.get(directory.document(id))
                    check(previous.getBoolean("isDeleted") != true) { "This teacher has been deleted. Add a new teacher profile." }
                }
                val userDocs = usersTask.result.documents.filter { doc -> doc.getString("role") == "teacher" }
                    .map { doc -> transaction.get(doc.reference) }
                val after = before.toMutableList()
                val originalConfig = config.toMap()
                val originalStudents = studentsTask.result.documents.map { doc ->
                    transaction.get(doc.reference).data.orEmpty() + ("id" to doc.id)
                }.toMutableList()
                val students=originalStudents.toMutableList()
                studentWrite?.let { (id, payload) ->
                    val oldStudent = transaction.get(root.collection("students").document(id)).data.orEmpty()
                    check(oldStudent["isDeleted"] != true) { "This student has been removed from the directory." }
                    if (payload["status"] in setOf("Active", "On Leave")) {
                        check(validScope(config, str(payload, "classId"), str(payload, "sectionId"), str(payload, "academicSessionId"))) {
                            "The selected class or section is inactive or was removed. Refresh and select an active section."
                        }
                    }
                    students.removeAll { it["id"] == id }
                    students.add(oldStudent + payload + ("id" to id))
                }
                edit(after, config, students)
                val renamedClasses = maps(config["classes"]).filter { updated ->
                    maps(originalConfig["classes"]).any { old -> old["id"] == updated["id"] && old["name"] != updated["name"] }
                }.associate { str(it, "id") to str(it, "name") }
                val renamedSections = maps(config["sections"]).filter { updated ->
                    maps(originalConfig["sections"]).any { old -> old["id"] == updated["id"] && old["name"] != updated["name"] }
                }.associate { str(it, "id") to str(it, "name") }
                val studentChanges = students.mapNotNull { student ->
                    val updates = mutableMapOf<String, Any>()
                    renamedClasses[str(student, "classId")]?.let { updates["className"] = it }
                    renamedSections[str(student, "sectionId")]?.let { updates["section"] = it }
                    if (updates.isEmpty()) null else str(student, "id") to updates
                }
                check(after.size + userDocs.size + studentChanges.size + (if (studentWrite == null) 0 else 1) + 1 < 500) { "Too many linked records for one update." }
                val projectedStudents = students.map { student ->
                    student + studentChanges.firstOrNull { it.first == student["id"] }?.second.orEmpty()
                }
                check(after.size + userDocs.size + 1 < 500) { "Directory size is too large for this operation." }
                val currentSession = maps(config["sessions"]).firstOrNull { it["isCurrent"] == true }?.get("id")?.toString()
                    ?: maps(config["sessions"]).firstOrNull { it["status"] == "Active" }?.get("id")?.toString().orEmpty()
                check(currentSession.isNotBlank() || pk.edunexa.app.academic.AcademicBootstrapPolicy.emptySchoolSetup(before.size,after.size,students.size,config,studentWrite!=null || teacherWriteId!=null)) { "Set an active academic session before linking people or assignments." }
                val allAssignments = maps(config["teacherAssignments"]).map { assignment ->
                    assignment + ("teacherName" to str(after.firstOrNull { it["id"] == assignment["teacherId"] }.orEmpty(), "name"))
                }
                config["teacherAssignments"] = allAssignments
                for (item in after) {
                    val teacherId = str(item, "id")
                    val own = allAssignments.filter { it["teacherId"] == teacherId && it["academicSession"] == currentSession }
                    val active = item["status"] == "Active"
                    val scopes = own.map { it["classId"] to it["sectionId"] }.toMutableSet()
                    if (active && str(item, "inchargeClassId").isNotBlank() &&
                        (str(item, "inchargeAcademicSession").isBlank() || item["inchargeAcademicSession"] == currentSession)) {
                        scopes.add(item["inchargeClassId"] to item["inchargeSectionId"])
                    }
                    val classIds = scopes.map { it.first }.toSet()
                    val sectionIds = scopes.map { it.second }.toSet()
                    val subjectIds = own.map { it["subjectId"] }.toSet()
                val scoped = mapOf(
                        "sessions" to maps(config["sessions"]),
                        "classes" to if (active) maps(config["classes"]).filter { it["id"] in classIds } else emptyList(),
                        "sections" to if (active) maps(config["sections"]).filter { it["id"] in sectionIds } else emptyList(),
                        "subjects" to if (active) maps(config["subjects"]).filter { it["id"] in subjectIds } else emptyList(),
                        "classSubjectAssignments" to maps(config["classSubjectAssignments"]).filter {
                            it["classId"] in classIds && it["subjectId"] in subjectIds && it["academicSession"] == currentSession
                        },
                        "teacherAssignments" to if (active) own else emptyList(),
                        "teacherRoster" to listOf(TeacherProjectionPolicy.rosterEntry(item)),
                        "studentRoster" to if (active) projectedStudents.filter { student ->
                            val studentSession = str(student, "academicSessionId").ifBlank { str(student, "academicSession") }
                            (student["classId"] to student["sectionId"]) in scopes &&
                                (studentSession.isBlank() || studentSession == currentSession)
                        }.map { student -> student.filterKeys { field ->
                            field in setOf("id", "fullName", "admissionNo", "rollNo", "classId", "sectionId", "className", "section", "status", "gender", "guardianPhone", "academicSession", "academicSessionId")
                        } } else emptyList()
                    )
                    val keys = if (active) own.map {
                        "${it["classId"]}__${it["sectionId"]}__${it["subjectId"]}"
                    }.distinct() else emptyList()
                    val payload = item.filterKeys { it !in setOf("academicDirectory", "subjectAccessKeys", "portalPassword") } + mapOf(
                        "academicDirectory" to scoped, "subjectAccessKeys" to keys, "activeAcademicSessionId" to currentSession,
                        "academicDirectoryUpdatedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                    )
                    val projectionChanged = item["academicDirectory"] != scoped || item["subjectAccessKeys"] != keys || item["activeAcademicSessionId"] != currentSession
                    val profileChanged = before.firstOrNull { it["id"] == teacherId } != item
                    if(profileChanged || projectionChanged) transaction.set(directory.document(teacherId), payload + pk.edunexa.app.activity.ActivityHistory.patch(before.firstOrNull { it["id"] == teacherId }.orEmpty(),payload,caller,admin.getString("name").orEmpty(),"admin","Teacher profile updated",str(item,"name")), SetOptions.merge())
                    if(studentWrite == null) userDocs.filter { it.getString("profileId") == teacherId }.forEach { user ->
                        val desiredActive = active && user.getBoolean("portalDisabled") != true
                        if (user.getBoolean("active") != desiredActive || user.getString("name") != str(item, "name"))
                            transaction.update(user.reference, mapOf("active" to desiredActive, "name" to str(item, "name"), "updatedAt" to FieldValue.serverTimestamp()))
                    }
                }
                studentWrite?.let { (id, payload) -> transaction.set(root.collection("students").document(id), payload + pk.edunexa.app.activity.ActivityHistory.patch(originalStudents.firstOrNull { it["id"] == id }.orEmpty(),payload,caller,admin.getString("name").orEmpty(),"admin","Student profile updated",payload["fullName"].toString()), SetOptions.merge()) }
                studentChanges.forEach { (id, changes) -> transaction.update(root.collection("students").document(id), changes) }
                transaction.update(configRef, mapOf(
                    "sessions" to maps(config["sessions"]), "classes" to maps(config["classes"]),
                    "sections" to maps(config["sections"]), "archivedSections" to maps(config["archivedSections"]),
                    "subjects" to maps(config["subjects"]), "classSubjectAssignments" to maps(config["classSubjectAssignments"]),
                    "teacherAssignments" to allAssignments, "teacherRegistryIds" to after.map { it["id"] },
                    "academicUpdatedAt" to FieldValue.serverTimestamp()
                ))
                Unit
            }.addOnSuccessListener { onResult(Result.success(Unit)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }
    }

    fun createLogin(schoolId: String, item: TeacherItem, email: String, password: String, onResult: (Result<Unit>) -> Unit) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() || password.length < 6) {
            onResult(Result.failure(IllegalArgumentException("Enter a valid email address and a password with at least 6 characters.")))
            return
        }
        pk.edunexa.app.auth.ProfileLoginRepository(db).create(schoolId, "teacher", item.id,
            pk.edunexa.app.auth.GeneratedLogin(cleanEmail, password), onResult)
    }

    private fun validScope(config: Map<String, Any?>, classId: String, sectionId: String, sessionId: String): Boolean =
        options(config, "classes").any { it.id == classId } &&
            options(config, "sections", "classId").any { it.id == sectionId && it.parentId == classId } &&
            options(config, "sessions", onlyActive = false).any { it.id == sessionId }

    private fun teacher(doc: DocumentSnapshot) = TeacherItem(
        id = doc.id, name = doc.getString("name").orEmpty(), employeeId = doc.getString("employeeId").orEmpty(),
        phone = doc.getString("phone").orEmpty(), email = doc.getString("email").orEmpty(),
        qualification = doc.getString("qualification").orEmpty(), joiningDate = doc.getString("joiningDate").orEmpty(),
        status = doc.getString("status").orEmpty(), portalEmail = doc.getString("portalEmail").orEmpty(),
        authUid = doc.getString("authUid").orEmpty(), inchargeClassId = doc.getString("inchargeClassId").orEmpty(),
        inchargeSectionId = doc.getString("inchargeSectionId").orEmpty(), inchargeAcademicSession = doc.getString("inchargeAcademicSession").orEmpty(),
        profilePhoto = doc.getString("profilePhoto")
    )

    private fun options(raw: Map<String, Any?>, key: String, parent: String = "", onlyActive: Boolean = true): List<AcademicOption> =
        maps(raw[key]).filter { !onlyActive || it["isActive"] == true }.sortedBy { (it["displayOrder"] as? Number)?.toInt() ?: Int.MAX_VALUE }
            .map { AcademicOption(str(it, "id"), str(it, "name"), str(it, parent)) }.filter { it.id.isNotBlank() }
    private fun assignments(raw: Map<String, Any?>) = maps(raw["teacherAssignments"]).map {
        TeacherAssignment(str(it, "id"), str(it, "teacherId"), str(it, "classId"), str(it, "sectionId"), str(it, "subjectId"), str(it, "academicSession"))
    }
    private fun maps(value: Any?): List<Map<String, Any?>> = (value as? List<*>)?.mapNotNull { entry ->
        (entry as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
    }.orEmpty()
    private fun str(value: Map<String, Any?>, key: String) = value[key]?.toString()?.trim().orEmpty()
}


