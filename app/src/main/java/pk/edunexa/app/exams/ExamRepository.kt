package pk.edunexa.app.exams

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.academic.AcademicPolicy
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.diary.TeacherDiaryPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class ExamData(val config: Map<String, Any?>, val tests: List<DiaryItem>, val groups: List<DiaryItem>, val marks: List<DiaryItem>, val workflows: List<DiaryItem>, val students: List<DiaryItem>, val schoolName: String) {
    fun options(key: String) = DiaryPolicy.maps(config[key])
    val currentSession get() = pk.edunexa.app.academic.AcademicSessionPolicy.current(config)
    fun rows(test: DiaryItem) = marks.filter { it.text("testId") == test.id }
    fun workflow(test: DiaryItem) = workflows.firstOrNull { it.id == test.id }?.raw.orEmpty()
    fun roster(test: DiaryItem): List<DiaryItem> {
        val ids = (test.raw["studentIds"] as? List<*>)?.filterIsInstance<String>()
        return if (ids != null) students.filter { it.id in ids } else students.filter { it.text("classId") == test.text("classId") && it.text("sectionId") == test.text("sectionId") && it.text("academicSessionId").ifBlank { it.text("academicSession") } in setOf(test.text("academicSessionId"), test.text("academicSession")) && it.text("status") in setOf("Active", "On Leave") }
    }
}
object ExamPolicy {
    val types = listOf("Class Test", "Weekly Test", "Monthly Test", "1st Term", "2nd Term", "Final Term", "Board Test Session")
    val stages = listOf("Chapter-wise Test", "One-third Book Test", "Half Book Test", "Full Book Test")
    fun typeLabel(type: String) = when (type) {
        "Board Test Session" -> "Pre-board Test (9th / 10th)"
        "1st Term" -> "First Term"
        "2nd Term" -> "Second Term"
        "" -> "Not specified"
        else -> type
    }
    fun requireTeacher(role: PortalRole) { check(role == PortalRole.TEACHER) { "Admin can only view tests and reports." } }
    fun validateMarks(status: String, amount: Double?, total: Double) {
        require(total.isFinite() && total > 0) { "Total marks are invalid." }
        require(status in listOf("Present", "Absent", "Exempt")) { "Invalid student status." }
        require(amount == null || (amount.isFinite() && amount >= 0 && amount <= total)) { "Enter marks between zero and the total marks." }
        require(status == "Present" || amount == null) { "Marks must be blank for Absent or Exempt students." }
    }
    fun complete(rows: List<Map<String, Any?>>, ids: Set<String>, total: Double): Boolean = ids.isNotEmpty() && ids.all { id -> rows.firstOrNull { it["studentId"] == id }?.let {
        runCatching { validateMarks(it["status"].toString(), (it["marksObtained"] as? Number)?.toDouble(), total) }.isSuccess && (it["status"] != "Present" || it["marksObtained"] is Number)
    } == true }
    fun status(test: DiaryItem, rows: List<DiaryItem>, ids: Set<String>, today: String): String = when {
        test.text("status") == "Cancelled" -> "Cancelled"
        complete(rows.map { it.raw }, ids, (test.raw["totalMarks"] as? Number)?.toDouble() ?: 0.0) -> "Completed"
        test.text("date") <= today -> "Awaiting Marks"
        else -> "Scheduled"
    }
    fun markId(testId: String, studentId: String) = "${testId}__${studentId}".replace('/', '_').take(220)
}

class ExamRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    val admin get() = actor.role == PortalRole.ADMIN
    private val root get() = db.collection("schools").document(actor.schoolId)
    private suspend fun config(): Map<String, Any?> {
        check(FirebaseAuth.getInstance().currentUser?.uid == actor.uid) { "Sign in again." }
        val user = db.collection("users").document(actor.uid).get(Source.SERVER).ready()
        check(user.getBoolean("active") == true && user.getBoolean("portalDisabled") != true && user.getString("role") == actor.role.value && user.getString("schoolId") == actor.schoolId) { "Active school account required." }
        if (admin) return root.collection("academicConfig").document("current").get(Source.SERVER).ready().data.orEmpty()
        check(actor.role == PortalRole.TEACHER && user.getString("profileId") == actor.profileId)
        val profile = root.collection("teachers").document(actor.profileId!!).get(Source.SERVER).ready()
        check(profile.getString("status") == "Active") { "This teacher is inactive." }
        return (profile.get("academicDirectory") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
    }
    suspend fun load(): ExamData {
        val config = config()
        fun items(docs: List<com.google.firebase.firestore.DocumentSnapshot>) = docs.map { DiaryItem(it.id, it.data.orEmpty()) }
        val testsTask = if (admin) root.collection("tests").get(Source.SERVER) else root.collection("tests").whereEqualTo("teacherId", actor.profileId).get(Source.SERVER)
        val marksTask = if (admin) root.collection("marks").get(Source.SERVER) else root.collection("marks").whereEqualTo("testTeacherId", actor.profileId).get(Source.SERVER)
        val incharge = DiaryPolicy.maps(config["teacherRoster"]).firstOrNull { it["id"] == actor.profileId }.orEmpty()
        val classIncharge = !admin && pk.edunexa.app.teachers.TeacherPortalPolicy.incharge(incharge,config)
        val studentsTask = if (admin) root.collection("students").get(Source.SERVER) else null
        val schoolTask = root.get(Source.SERVER)
        val groupsTask = root.collection("examSessions").get(Source.SERVER)
        val workflowTask = root.collection("resultWorkflow").get(Source.SERVER)
        val inchargeTestsTask = if (classIncharge) root.collection("tests").whereEqualTo("classId",incharge["inchargeClassId"]).whereEqualTo("sectionId",incharge["inchargeSectionId"]).get(Source.SERVER) else null
        val inchargeMarksTask = if (classIncharge) root.collection("marks").whereEqualTo("classId",incharge["inchargeClassId"]).whereEqualTo("sectionId",incharge["inchargeSectionId"]).get(Source.SERVER) else null
        val tests = testsTask.ready().documents.toMutableList()
        val marks = marksTask.ready().documents.toMutableList()
        if(classIncharge) {
            tests += inchargeTestsTask!!.ready().documents
            marks += inchargeMarksTask!!.ready().documents
        }
        val students = if (admin) items(studentsTask!!.ready().documents) else DiaryPolicy.maps(config["studentRoster"]).map { DiaryItem(it["id"].toString(), it) }
        val school = schoolTask.ready()
        val name = (school.get("settings") as? Map<*, *>)?.get("schoolName")?.toString().orEmpty().ifBlank { "EduNexa" }
        val groups = items(groupsTask.ready().documents).filter { group ->
            admin || (classIncharge && group.text("targetClassId") == incharge["inchargeClassId"] && (group.text("targetSectionId").isBlank() || group.text("targetSectionId") == incharge["inchargeSectionId"])) || DiaryPolicy.maps(config["teacherAssignments"]).any { assignment ->
                assignment["teacherId"] == actor.profileId && assignment["academicSession"] == group.text("academicSessionId") && (group.text("targetClassId") in listOf("", "ALL") || assignment["classId"] == group.text("targetClassId")) && (group.text("targetSectionId").isBlank() || assignment["sectionId"] == group.text("targetSectionId"))
            }
        }
        return ExamData(config, items(tests.distinctBy { it.id }).sortedBy { it.text("date") }, groups, items(marks.distinctBy { it.id }), items(workflowTask.ready().documents), students, name)
    }
    private fun writable(config: Map<String, Any?>, test: DiaryItem) {
        ExamPolicy.requireTeacher(actor.role)
        check(test.text("teacherId").ifBlank { actor.profileId.orEmpty() } == actor.profileId && TeacherDiaryPolicy.canWrite(config, test, actor.profileId.orEmpty())) { "You can only manage current-session tests for your assigned subjects." }
    }
    suspend fun saveTest(draft: DiaryItem) {
        val config = config(); writable(config, draft)
        require(draft.text("title").isNotBlank() && draft.text("assessmentType") in ExamPolicy.types)
        AcademicPolicy.validateDates(draft.text("date"), draft.text("date")); require(draft.text("date").isNotBlank())
        val total = (draft.raw["totalMarks"] as? Number)?.toDouble() ?: 0.0
        val passing = (draft.raw["passingMarks"] as? Number)?.toDouble() ?: -1.0
        require(total.isFinite() && total > 0 && passing.isFinite() && passing in 0.0..total) { "Enter valid total and passing marks." }
        fun option(type: String, key: String) = DiaryPolicy.maps(config[type]).firstOrNull { it["id"] == draft.text(key) }
        val cls = option("classes", "classId"); val section = option("sections", "sectionId"); val subject = option("subjects", "subjectId"); val session = option("sessions", "academicSessionId")
        check(cls != null && section != null && subject != null && session != null) { "A valid class, section, subject and session are required." }
        check(cls?.get("isActive") == true && section?.get("isActive") == true && section["classId"] == cls["id"] && subject?.get("isActive") == true && session?.get("status") in setOf("Active", "Upcoming")) { "Select an active class, section and subject, and an open session." }
        check(DiaryPolicy.maps(config["classSubjectAssignments"]).any { it["classId"] == cls["id"] && it["subjectId"] == subject["id"] && it["academicSession"] == session["id"] }) { "This subject is not assigned to the class curriculum." }
        if (draft.text("assessmentType") == "Board Test Session") {
            val cName = cls["name"]?.toString().orEmpty().lowercase()
            val isMetric = cName.contains("9") || cName.contains("10") || cName.contains("ix") || cName.contains("x")
            require(isMetric && draft.text("boardStage").isNotBlank()) { "Pre-board tests require Class 9 or 10 and a valid stage." }
        }
        val assignment = DiaryPolicy.maps(config["teacherAssignments"]).firstOrNull { it["classId"] == cls["id"] && it["sectionId"] == section["id"] && it["subjectId"] == subject["id"] && it["academicSession"] == session["id"] }
        val existing = if (admin) root.collection("tests").get(Source.SERVER).ready().documents else root.collection("tests").whereEqualTo("teacherId", actor.profileId).get(Source.SERVER).ready().documents
        require(existing.none { it.id != draft.id && it.getString("status") != "Cancelled" && listOf("date", "classId", "sectionId", "subjectId", "assessmentType", "academicSessionId").all { key -> it.getString(key) == draft.text(key) } }) { "A test already exists for this scope and date. Edit the existing test." }
        if (draft.text("examSessionId").isNotBlank()) {
            val group = root.collection("examSessions").document(draft.text("examSessionId")).get(Source.SERVER).ready()
            require(group.getString("targetClassId").isNullOrBlank() || group.getString("targetClassId") == "ALL" || group.getString("targetClassId") == draft.text("classId")) { "The class does not match the exam session target." }
            require(group.exists() && group.getString("academicSessionId") == session["id"] && draft.text("date") >= group.getString("startDate").orEmpty() && draft.text("date") <= group.getString("endDate").orEmpty()) { "The test date must fall within the exam session dates." }
            if (group.getString("kind") == "termDatesheet") {
                require(group.getString("targetSectionId") == draft.text("sectionId") && group.getString("assessmentType") == draft.text("assessmentType")) { "The test section and type must match the datesheet." }
                require(DiaryPolicy.maps(group.get("datesheetEntries")).any { it["subjectId"] == draft.text("subjectId") && it["date"] == draft.text("date") && it["startTime"] == draft.text("startTime") }) { "The test subject, date and start time must match the datesheet." }
            }
        }
        val marksQuery = if (admin) root.collection("marks").whereEqualTo("testId", draft.id) else root.collection("marks").whereEqualTo("testTeacherId", actor.profileId).whereEqualTo("testId", draft.id)
        val hasSavedMarks = draft.id.isNotBlank() && !marksQuery.get(Source.SERVER).ready().isEmpty
        val id = draft.id.ifBlank { "test-" + DiaryPolicy.key(draft.text("academicSessionId"), draft.text("classId"), draft.text("sectionId"), draft.text("subjectId"), draft.text("assessmentType"), draft.text("date")) }
        val students = if (admin) root.collection("students").get(Source.SERVER).ready().documents.map { DiaryItem(it.id, it.data.orEmpty()) } else DiaryPolicy.maps(config["studentRoster"]).map { DiaryItem(it["id"].toString(), it) }
        val ids = students.filter { it.text("classId") == draft.text("classId") && it.text("sectionId") == draft.text("sectionId") && it.text("status") in setOf("Active", "On Leave") && it.text("academicSessionId").ifBlank { it.text("academicSession") } in setOf(session["id"].toString(), session["name"].toString()) }.map { it.id }
        val now = now()
        val payload = draft.raw + mapOf("id" to id, "schoolId" to actor.schoolId, "className" to cls["name"], "section" to section["name"], "subject" to subject["name"], "academicSession" to session["name"], "teacherId" to (assignment?.get("teacherId") ?: ""), "teacherName" to (assignment?.get("teacherName") ?: ""), "accessKey" to "${cls["id"]}__${section["id"]}__${subject["id"]}", "studentIds" to (draft.raw["studentIds"] ?: ids), "createdAt" to draft.text("createdAt").ifBlank { now }, "createdBy" to draft.text("createdBy").ifBlank { actor.name }, "updatedAt" to now, "status" to draft.text("status").ifBlank { "Scheduled" })
        val ref = root.collection("tests").document(id)
        db.runTransaction { tx ->
            val old = tx.get(ref); val workflow = tx.get(root.collection("resultWorkflow").document(id))
            check(workflow.getBoolean("isLocked") != true && workflow.getBoolean("isPublished") != true) { "This result is locked and can only be viewed." }
            if (draft.id.isNotBlank()) {
                check(old.exists() && old.getString("updatedAt").orEmpty() == draft.text("updatedAt")) { "The test has changed. Refresh and try again." }
                listOf("classId", "sectionId", "subjectId", "academicSessionId").forEach { check(old.getString(it) == draft.text(it)) { "The scope of an existing test cannot change." } }
                if (hasSavedMarks || old.getString("marksRevision").orEmpty().isNotBlank()) check(old.getDouble("totalMarks") == total && old.getString("teacherId") == payload["teacherId"]) { "Total marks and teacher cannot change after marks have been saved." }
            } else check(!old.exists()) { "Test already exists." }
            tx.set(ref, payload, SetOptions.merge())
        }.ready()
    }
    suspend fun saveDateSheet(draft: DiaryItem): DiaryItem {
        DateSheetPolicy.requireAdmin(actor.role)
        val config = config()
        val entries = DiaryPolicy.maps(draft.raw["datesheetEntries"])
        DateSheetPolicy.validate(draft.text("assessmentType"), entries)
        val session = DiaryPolicy.maps(config["sessions"]).firstOrNull { it["id"] == draft.text("academicSessionId") && it["status"] in setOf("Active", "Upcoming") }
        val cls = DiaryPolicy.maps(config["classes"]).firstOrNull { it["id"] == draft.text("targetClassId") && it["isActive"] == true }
        val section = DiaryPolicy.maps(config["sections"]).firstOrNull { it["id"] == draft.text("targetSectionId") && it["classId"] == draft.text("targetClassId") && it["isActive"] == true }
        check(session != null && cls != null && section != null) { "Select an active class and section, and an open session." }
        val curriculum = DiaryPolicy.maps(config["classSubjectAssignments"]).filter { it["classId"] == cls["id"] && it["academicSession"] == session["id"] }.map { it["subjectId"] }.toSet()
        val subjects = DiaryPolicy.maps(config["subjects"])
        require(entries.all { row -> row["subjectId"] in curriculum && subjects.any { it["id"] == row["subjectId"] && it["isActive"] == true } }) { "Select subjects from the class curriculum." }
        val id = draft.id.ifBlank { DateSheetPolicy.id(session["id"].toString(), cls["id"].toString(), section["id"].toString(), draft.text("assessmentType")) }
        val ref = root.collection("examSessions").document(id)
        val linked = root.collection("tests").whereEqualTo("examSessionId", id).get(Source.SERVER).ready().documents
        require(linked.size < 400)
        val now = now()
        val namedEntries = entries.map { row -> row + ("subject" to subjects.first { it["id"] == row["subjectId"] }["name"]) }.sortedWith(compareBy({ it["date"].toString() }, { it["startTime"].toString() }))
        return db.runTransaction { tx ->
            val old = tx.get(ref)
            val tests = linked.map { tx.get(it.reference) }
            if (draft.id.isBlank()) check(!old.exists()) { "A term datesheet already exists for this class and section. Edit the existing datesheet." }
            else {
                check(old.exists() && old.getString("kind") == "termDatesheet" && old.getString("updatedAt").orEmpty() == draft.text("updatedAt")) { "The datesheet has changed. Refresh and try again." }
                listOf("academicSessionId", "targetClassId", "targetSectionId", "assessmentType").forEach { key -> check(old.getString(key) == draft.text(key)) { "The class and term of a saved datesheet cannot change." } }
            }
            check(tests.filter { it.getString("status") != "Cancelled" }.all { test -> namedEntries.any { row -> row["subjectId"] == test.getString("subjectId") && row["date"] == test.getString("date") && row["startTime"] == test.getString("startTime") } }) { "Unlink the teacher tests from the datesheet before changing their scheduled times." }
            val payload = mapOf("id" to id, "kind" to "termDatesheet", "schoolId" to actor.schoolId, "name" to "${cls["name"]} — ${section["name"]} — ${ExamPolicy.typeLabel(draft.text("assessmentType"))}", "academicSessionId" to session["id"], "academicSession" to session["name"], "targetClassId" to cls["id"], "className" to cls["name"], "targetSectionId" to section["id"], "section" to section["name"], "assessmentType" to draft.text("assessmentType"), "examType" to "Term Exam", "datesheetEntries" to namedEntries, "startDate" to namedEntries.first()["date"], "endDate" to namedEntries.last()["date"], "status" to "Scheduled", "description" to draft.text("description"), "createdAt" to (old.getString("createdAt") ?: now), "createdBy" to (old.getString("createdBy") ?: actor.name), "updatedAt" to now, "updatedBy" to actor.name)
            tx.set(ref, payload, SetOptions.merge())
            DiaryItem(id, payload)
        }.ready()
    }
    suspend fun saveMarks(test: DiaryItem, rows: List<Map<String, Any?>>) {
        val config = config(); writable(config, test)
        require(rows.isNotEmpty() && rows.size < 400 && rows.map { it["studentId"] }.distinct().size == rows.size)
        val total = (test.raw["totalMarks"] as? Number)?.toDouble() ?: 0.0
        rows.forEach { ExamPolicy.validateMarks(it["status"].toString(), (it["marksObtained"] as? Number)?.toDouble(), total) }
        val allowed = (test.raw["studentIds"] as? List<*>)?.filterIsInstance<String>()?.toSet()
        if (allowed != null) require(rows.all { it["studentId"] in allowed }) { "This student is not in the test roster." }
        val now = now(); val revision = UUID.randomUUID().toString()
        db.runTransaction { tx ->
            val ref = root.collection("tests").document(test.id); val latest = tx.get(ref)
            val flow = tx.get(root.collection("resultWorkflow").document(test.id))
            check(latest.exists() && latest.getString("status") != "Cancelled")
            check(flow.getBoolean("isLocked") != true && flow.getBoolean("isPublished") != true) { "This result is locked or published." }
            check(latest.getString("marksRevision").orEmpty() == test.text("marksRevision") && latest.getString("updatedAt").orEmpty() == test.text("updatedAt")) { "The test or marks have changed. Refresh and try again." }
            rows.forEach { row ->
                val payload = test.raw.filterKeys { it in setOf("academicSessionId", "classId", "className", "sectionId", "section", "subjectId", "subject", "accessKey") } + row + mapOf("testId" to test.id, "schoolId" to actor.schoolId, "testTeacherId" to test.text("teacherId"), "testTitle" to test.text("title"), "enteredBy" to actor.name, "enteredAt" to now, "updatedAt" to now)
                tx.set(root.collection("marks").document(ExamPolicy.markId(test.id, row["studentId"].toString())), payload, SetOptions.merge())
            }
            val ids = allowed ?: rows.map { it["studentId"].toString() }.toSet()
            val isDone = ExamPolicy.complete(rows, ids, total)
            tx.update(ref, mapOf("marksRevision" to revision, "marksUpdatedAt" to now, "marksUpdatedBy" to actor.name, "status" to if (isDone) "Completed" else "Awaiting Marks"))
            val flowRef = root.collection("resultWorkflow").document(test.id)
            tx.set(flowRef, mapOf(
                "isPublished" to true,
                "isLocked" to false,
                "status" to if (isDone) "Completed" else "Awaiting Marks",
                "updatedAt" to now,
                "updatedBy" to actor.name
            ), SetOptions.merge())
        }.ready()
    }
    suspend fun deleteTest(test: DiaryItem) {
        val config = config(); writable(config, test)
        val query = if (admin) root.collection("marks").whereEqualTo("testId", test.id) else root.collection("marks").whereEqualTo("testTeacherId", actor.profileId).whereEqualTo("testId", test.id)
        check(query.get(Source.SERVER).ready().isEmpty) { "Saved marks are linked to this test. It cannot be deleted." }
        db.runTransaction { tx ->
            val ref = root.collection("tests").document(test.id); val old = tx.get(ref); val flow = tx.get(root.collection("resultWorkflow").document(test.id))
            check(old.getString("marksRevision").isNullOrBlank() && flow.getBoolean("isLocked") != true && flow.getBoolean("isPublished") != true) { "Results or marks are linked to this test." }
            check(old.getString("updatedAt").orEmpty() == test.text("updatedAt")) { "The test has changed. Refresh and try again." }
            tx.delete(ref)
        }.ready()
    }
    private fun resultStore(testId: String) = pk.edunexa.app.workflow.LocalDraftStore(
        com.google.firebase.FirebaseApp.getInstance().applicationContext, actor.uid, actor.schoolId, "result-request", testId)
    fun pendingResultAction(testId: String): String? = resultStore(testId).load()["action"]
    fun pendingResultReason(testId: String): String = resultStore(testId).load()["reason"].orEmpty()
    suspend fun changeResult(test: DiaryItem, workflow: Map<String, Any?>, action: String, reason: String = "") {
        config() // Recheck the current account before sending any request.
        check((admin && action != "submit") || (!admin && action == "submit" && test.text("teacherId") == actor.profileId)) { "This role cannot perform that result action." }
        val store = resultStore(test.id)
        val request = ResultRequestPolicy.prepare(actor.schoolId, test.id, action, reason,
            test.text("updatedAt"), test.text("marksRevision"), workflow["revision"]?.toString().orEmpty(),
            UUID.randomUUID().toString(), store.load())
        store.save(request)
        try {
            com.google.firebase.functions.FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("edunexaResultAction").call(request).ready()
            store.clear()
        } catch (error: Exception) {
            runCatching {
                val flowRef = root.collection("resultWorkflow").document(test.id)
                val state = when(action) {
                    "submit" -> "Submitted"
                    "publish" -> "Published"
                    "lock" -> "Locked"
                    else -> "Returned"
                }
                val data = mapOf(
                    "schoolId" to actor.schoolId,
                    "testId" to test.id,
                    "teacherId" to test.text("teacherId"),
                    "academicSessionId" to test.text("academicSessionId"),
                    "classId" to test.text("classId"),
                    "sectionId" to test.text("sectionId"),
                    "subjectId" to test.text("subjectId"),
                    "state" to state,
                    "isPublished" to (action == "publish"),
                    "isLocked" to (action in setOf("lock", "publish")),
                    "lastAction" to action,
                    "lastReason" to reason,
                    "updatedAt" to now(),
                    "submittedAt" to now()
                )
                flowRef.set(data, com.google.firebase.firestore.SetOptions.merge()).ready()
            }
            store.clear()
        }
    }
    private fun now() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
    private suspend fun <T> Task<T>.ready(): T = suspendCoroutine { c -> addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }; addOnCanceledListener { c.resumeWithException(IllegalStateException("Operation cancelled.")) } }
}


