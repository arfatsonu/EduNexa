package pk.edunexa.app.attendance

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import pk.edunexa.app.students.StudentClassOption
import pk.edunexa.app.students.StudentDirectoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.google.firebase.firestore.DocumentSnapshot
import pk.edunexa.app.academic.AcademicSessionPolicy

data class AttendanceStudent(
    val id: String,
    val fullName: String,
    val admissionNo: String,
    val rollNo: String,
    val guardianPhone: String
)

data class AttendanceEntry(
    val studentId: String,
    val status: String,
    val remarks: String,
    val markedBy: String,
    val markedAt: String,
    val followUpComment: String,
    val followUpUpdatedBy: String,
    val followUpUpdatedAt: String,
    val recordId: String,
    val arrivalTime: String = ""
)

data class AttendanceBlock(
    val key: String,
    val classId: String,
    val className: String,
    val sectionId: String,
    val sectionName: String,
    val students: List<AttendanceStudent>,
    val entries: Map<String, AttendanceEntry>,
    val status: String,
    val present: Int,
    val absent: Int,
    val leave: Int,
    val late: Int,
    val markedBy: String,
    val markedAt: String,
    val lateCountsAsPresent: Boolean = true,
    val excludeLeave: Boolean = true,
    val minimumPercent: Double = 75.0
) {
    val total: Int get() = students.size
    val recorded: Int get() = entries.keys.intersect(students.map { it.id }.toSet()).size
    val attendanceRate: Int
        get() = pk.edunexa.app.settings.AttendancePolicy.rate(present, absent, late, leave, lateCountsAsPresent, excludeLeave)?.toInt() ?: 0
}

data class AttendanceOverview(
    val blocks: List<AttendanceBlock>
) {
    val marked: Int get() = blocks.count { it.status == "Marked" }
    val pending: Int get() = blocks.count { it.status == "Pending" }
    val incomplete: Int get() = blocks.count { it.status == "Incomplete" }
    val students: Int get() = blocks.sumOf { it.total }
    val present: Int get() = blocks.sumOf { it.present }
    val absent: Int get() = blocks.sumOf { it.absent }
    val leave: Int get() = blocks.sumOf { it.leave }
    val late: Int get() = blocks.sumOf { it.late }
}

class AdminAttendanceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val studentDirectoryRepository = StudentDirectoryRepository(db)

    fun loadAcademicOptions(
        schoolId: String,
        onResult: (Result<List<StudentClassOption>>) -> Unit
    ) = studentDirectoryRepository.loadAcademicOptions(schoolId, onResult)

    fun loadOverview(schoolId: String, academicSessionId: String, date: String, onResult: (Result<AttendanceOverview>) -> Unit) {
        db.collection("schools").document(schoolId).get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { school ->
                val settings = school.get("settings") as? Map<*, *>
                loadRawOverview(schoolId, academicSessionId, date) { result ->
                    onResult(result.map { overview -> overview.copy(blocks = overview.blocks.map { it.copy(lateCountsAsPresent = settings?.get("lateCountsAsPresent") as? Boolean ?: true, excludeLeave = settings?.get("excludeLeaveFromAttendance") as? Boolean ?: true, minimumPercent = (settings?.get("attendanceMinimumPercent") as? Number)?.toDouble() ?: 75.0) }) })
                }
            }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun loadRawOverview(
        schoolId: String,
        academicSessionId: String,
        date: String,
        onResult: (Result<AttendanceOverview>) -> Unit
    ) {
        db.collection("schools").document(schoolId).collection("academicConfig").document("current").get(com.google.firebase.firestore.Source.SERVER)
            .addOnFailureListener { onResult(Result.failure(IllegalStateException("Could not verify the academic session.", it))) }
            .addOnSuccessListener { config ->
        val configured = (config.get("sessions") as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull { it["id"] == academicSessionId || it["name"] == academicSessionId }
        val aliases = listOf(academicSessionId, configured?.get("id")?.toString().orEmpty(), configured?.get("name")?.toString().orEmpty())
        loadAcademicOptions(schoolId) { optionsResult ->
            optionsResult.onFailure {
                onResult(Result.failure(it))
            }.onSuccess { options ->
                val school = db.collection("schools").document(schoolId)
                school.collection("students").get(com.google.firebase.firestore.Source.SERVER)
                    .addOnSuccessListener { studentSnapshot ->
                        school.collection("attendance")
                            .whereEqualTo("attendanceDate", date)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener { attendanceSnapshot ->
                                val activeStudents = studentSnapshot.documents.mapNotNull { doc ->
                                    val status = doc.getString("status").orEmpty()
                                    if (!AcademicSessionPolicy.enrolled(status) || !AcademicSessionPolicy.matches(doc.getString("academicSessionId").orEmpty().ifBlank { doc.getString("academicSession").orEmpty() }, aliases, allowMissing = true)) {
                                        return@mapNotNull null
                                    }
                                    val classId = doc.getString("classId").orEmpty()
                                    val sectionId = doc.getString("sectionId").orEmpty()
                                    if (classId.isBlank() || sectionId.isBlank()) return@mapNotNull null

                                    StudentScoped(
                                        classId = classId,
                                        sectionId = sectionId,
                                        student = AttendanceStudent(
                                            id = doc.id,
                                            fullName = doc.getString("fullName").orEmpty(),
                                            admissionNo = doc.getString("admissionNo").orEmpty(),
                                            rollNo = doc.getString("rollNo").orEmpty(),
                                            guardianPhone = doc.getString("guardianPhone").orEmpty()
                                        )
                                    )
                                }

                                val attendanceDocs = attendanceSnapshot.documents.filter { doc ->
                                    AcademicSessionPolicy.matches(doc.getString("academicSessionId").orEmpty().ifBlank { doc.getString("academicSession").orEmpty() }, aliases)
                                }

                                val blocks = options.flatMap { cls ->
                                    cls.sections.map { section ->
                                        val students = activeStudents
                                            .filter { it.classId == cls.id && it.sectionId == section.id }
                                            .map { it.student }
                                            .sortedWith(
                                                compareBy<AttendanceStudent>(
                                                    { it.rollNo.toIntOrNull() ?: Int.MAX_VALUE },
                                                    { it.fullName.lowercase(Locale.getDefault()) }
                                                )
                                            )

                                        val ids = students.map { it.id }.toSet()
                                        val matching = attendanceDocs.filter { doc ->
                                            doc.getString("classId").orEmpty() == cls.id &&
                                                doc.getString("sectionId").orEmpty() == section.id &&
                                                doc.getString("studentId").orEmpty() in ids
                                        }

                                        val entries = matching.groupBy { it.getString("studentId") }.values.map { rows -> rows.maxWithOrNull(compareBy<DocumentSnapshot>({ it.getString("lastUpdatedAt").orEmpty().ifBlank { it.getString("updatedAt").orEmpty().ifBlank { it.getString("markedAt").orEmpty() } } }, { it.id }))!! }.mapNotNull { doc ->
                                            val studentId = doc.getString("studentId").orEmpty()
                                            if (studentId.isBlank() || normalizeStatus(doc.getString("status").orEmpty()) == "Unmarked") return@mapNotNull null
                                            studentId to AttendanceEntry(
                                                studentId = studentId,
                                                status = normalizeStatus(doc.getString("status").orEmpty()),
                                                remarks = doc.getString("remarks")
                                                    ?: doc.getString("note")
                                                    ?: "",
                                                markedBy = doc.getString("markedBy").orEmpty(),
                                                markedAt = doc.getString("markedAt").orEmpty(),
                                                followUpComment = doc.getString("followUpComment").orEmpty(),
                                                followUpUpdatedBy = doc.getString("followUpUpdatedBy").orEmpty(),
                                                followUpUpdatedAt = doc.getString("followUpUpdatedAt").orEmpty(),
                                                recordId = doc.id, arrivalTime = doc.getString("arrivalTime").orEmpty()
                                            )
                                        }.toMap()

                                        val recorded = entries.keys.intersect(ids).size
                                        val blockStatus = when {
                                            students.isEmpty() -> "No Students"
                                            recorded == 0 -> "Pending"
                                            recorded >= students.size -> "Marked"
                                            else -> "Incomplete"
                                        }

                                        AttendanceBlock(
                                            key = cls.id + "::" + section.id,
                                            classId = cls.id,
                                            className = cls.name,
                                            sectionId = section.id,
                                            sectionName = section.name,
                                            students = students,
                                            entries = entries,
                                            status = blockStatus,
                                            present = entries.values.count { it.status == "Present" },
                                            absent = entries.values.count { it.status == "Absent" },
                                            leave = entries.values.count { it.status == "Leave" },
                                            late = entries.values.count { it.status == "Late" },
                                            markedBy = entries.values.firstOrNull { it.markedBy.isNotBlank() }?.markedBy.orEmpty(),
                                            markedAt = entries.values.firstOrNull { it.markedAt.isNotBlank() }?.markedAt.orEmpty()
                                        )
                                    }
                                }

                                onResult(Result.success(AttendanceOverview(blocks)))
                            }
                            .addOnFailureListener { error ->
                                onResult(Result.failure(IllegalStateException("Could not load attendance records.", error)))
                            }
                    }
                    .addOnFailureListener { error ->
                        onResult(Result.failure(IllegalStateException("Could not load students.", error)))
                    }
            }
        }
    }

    }

    fun saveAbsentFollowUp(
        schoolId: String,
        academicSessionId: String,
        date: String,
        block: AttendanceBlock,
        studentId: String,
        comment: String,
        updatedBy: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanComment = comment.trim()
        if (cleanComment.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("The absent reason or follow-up note cannot be empty.")))
            return
        }

        val entry = block.entries[studentId]
        if (entry == null || entry.status != "Absent") {
            onResult(Result.failure(IllegalStateException("Follow-up notes can only be saved for absent students.")))
            return
        }

        val attendance = db.collection("schools").document(schoolId).collection("attendance")
        db.collection("schools").document(schoolId).collection("academicConfig").document("current").get(com.google.firebase.firestore.Source.SERVER)
            .addOnFailureListener { onResult(Result.failure(it)) }
            .addOnSuccessListener { config ->
        val configured = (config.get("sessions") as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull { it["id"] == academicSessionId || it["name"] == academicSessionId }
        val aliases = listOf(academicSessionId, configured?.get("id")?.toString().orEmpty(), configured?.get("name")?.toString().orEmpty())
        if (entry.recordId.isBlank()) {
            onResult(Result.failure(IllegalStateException("Refresh attendance before saving the reason.")))
            return@addOnSuccessListener
        }
        val reference = attendance.document(entry.recordId)
        val now = isoNow()
        db.runTransaction { tx ->
            val live = tx.get(reference)
            check(live.exists() && normalizeStatus(live.getString("status").orEmpty()) == "Absent" &&
                live.getString("studentId") == studentId && live.getString("attendanceDate") == date &&
                live.getString("classId") == block.classId && live.getString("sectionId") == block.sectionId &&
                AcademicSessionPolicy.matches(live.getString("academicSessionId").orEmpty().ifBlank { live.getString("academicSession").orEmpty() }, aliases)) {
                "Attendance changed. Refresh before saving a follow-up note."
            }
            tx.update(reference, mapOf(
                "followUpComment" to cleanComment,
                "followUpUpdatedBy" to updatedBy.ifBlank { "School Administrator" },
                "followUpUpdatedAt" to now,
                "cloudUpdatedAt" to FieldValue.serverTimestamp()
            ) + pk.edunexa.app.activity.ActivityHistory.patch(live.data.orEmpty(),mapOf("followUpComment" to cleanComment),com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in again"),updatedBy,"admin","Absent follow-up saved",live.getString("studentName").orEmpty(),now))
            Unit
        }.addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(IllegalStateException("Could not save the absent student's follow-up note.", it))) }
        }
    }

    fun saveAdminOverride(
        schoolId: String,
        academicSessionId: String,
        date: String,
        block: AttendanceBlock,
        statuses: Map<String, String>,
        remarks: Map<String, String>,
        reason: String,
        adminName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanReason = reason.trim()
        if (cleanReason.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("An Admin Override reason is required.")))
            return
        }
        if (block.status != "Pending" && block.status != "Incomplete") {
            onResult(Result.failure(IllegalStateException("Attendance is already complete. Admin Override is locked.")))
            return
        }
        val missing = block.students.filter { normalizeStatus(statuses[it.id].orEmpty()) == "Unmarked" }
        if (missing.isNotEmpty()) {
            onResult(Result.failure(IllegalArgumentException(missing.size.toString() + " students are still unmarked.")))
            return
        }

        // Re-check immediately before write so Admin cannot overwrite a class
        // that the Class Incharge completed after this screen was opened.
        loadOverview(schoolId, academicSessionId, date) { latestResult ->
            latestResult.onFailure { onResult(Result.failure(it)) }
                .onSuccess { latest ->
                    val current = latest.blocks.firstOrNull { it.key == block.key }
                    if (current == null) {
                        onResult(Result.failure(IllegalStateException("Could not verify this attendance block.")))
                        return@onSuccess
                    }
                    if (current.status == "Marked") {
                        onResult(
                            Result.failure(
                                IllegalStateException(
                                    "The Class Incharge has completed attendance. Admin Override is now locked."
                                )
                            )
                        )
                        return@onSuccess
                    }
                    if (current.status != "Pending" && current.status != "Incomplete") {
                        onResult(Result.failure(IllegalStateException("Admin Override is unavailable for this block.")))
                        return@onSuccess
                    }

                    if (current.students.any { normalizeStatus(statuses[it.id].orEmpty()) == "Unmarked" }) {
                        onResult(Result.failure(IllegalArgumentException("The student roster changed. Refresh and mark every student before saving.")))
                        return@onSuccess
                    }
                    val school = db.collection("schools").document(schoolId)
                    val attendance = school.collection("attendance")
                    attendance.whereEqualTo("attendanceDate", date).get(com.google.firebase.firestore.Source.SERVER).addOnFailureListener { onResult(Result.failure(it)) }.addOnSuccessListener { before ->
                    db.runTransaction { tx ->
                    val config = tx.get(school.collection("academicConfig").document("current"))
                    val configured = (config.get("sessions") as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull { it["id"] == academicSessionId || it["name"] == academicSessionId }
                    val aliases = listOf(academicSessionId, configured?.get("id")?.toString().orEmpty(), configured?.get("name")?.toString().orEmpty())
                    val canonical = current.students.map { attendance.document(attendanceDocId(academicSessionId, date, current.classId, current.sectionId, it.id)) }
                    val known = before.documents.filter { it.getString("classId") == current.classId && it.getString("sectionId") == current.sectionId && AcademicSessionPolicy.matches(it.getString("academicSessionId").orEmpty().ifBlank { it.getString("academicSession").orEmpty() }, aliases) }.map { it.reference }
                    val savedDocuments = (canonical + known).distinctBy { it.path }.map { tx.get(it) }.filter { it.exists() }
                    val records = savedDocuments.groupBy { it.getString("studentId") }.mapValues { (_, rows) -> rows.maxWithOrNull(compareBy<DocumentSnapshot>({ it.getString("lastUpdatedAt").orEmpty().ifBlank { it.getString("markedAt").orEmpty() } }, { it.id }))!! }
                    check(current.students.any { normalizeStatus(records[it.id]?.getString("status").orEmpty()) == "Unmarked" }) { "Attendance is now complete. Admin Override is locked." }
                    current.students.forEach { student ->
                        val live = tx.get(school.collection("students").document(student.id))
                        check(live.exists() && live.getString("classId") == current.classId && live.getString("sectionId") == current.sectionId && AcademicSessionPolicy.enrolled(live.getString("status").orEmpty()) && AcademicSessionPolicy.matches(live.getString("academicSessionId").orEmpty().ifBlank { live.getString("academicSession").orEmpty() }, aliases, true)) { "The student roster changed. Refresh attendance." }
                        check(normalizeStatus(records[student.id]?.getString("status").orEmpty()) == current.entries[student.id]?.status.orEmpty().ifBlank { "Unmarked" }) { "Attendance changed on another device. Refresh before overriding." }
                    }
                    val now = isoNow()
                    val markedBy = "Admin Override · " + adminName

                    current.students.forEach { student ->
                        val status = normalizeStatus(statuses[student.id].orEmpty())
                        val documentId = attendanceDocId(
                            academicSessionId,
                            date,
                            current.classId,
                            current.sectionId,
                            student.id
                        )
                        val ref = attendance.document(documentId)
                        val payload = hashMapOf<String, Any?>(
                            "id" to documentId,
                            "schoolId" to schoolId,
                            "academicSessionId" to academicSessionId,
                            "academicSession" to academicSessionId,
                            "attendanceDate" to date,
                            "date" to date,
                            "classId" to current.classId,
                            "className" to current.className,
                            "sectionId" to current.sectionId,
                            "section" to current.sectionName,
                            "studentId" to student.id,
                            "admissionNo" to student.admissionNo,
                            "rollNo" to student.rollNo,
                            "studentName" to student.fullName,
                            "status" to status,
                            "note" to remarks[student.id].orEmpty().trim(),
                            "remarks" to remarks[student.id].orEmpty().trim(),
                            "markedBy" to markedBy,
                            "markedAt" to now,
                            "lastUpdatedAt" to now,
                            "adminOverride" to true,
                            "adminOverrideReason" to cleanReason,
                            "adminOverrideBy" to adminName,
                            "adminOverrideAt" to now,
                            "cloudUpdatedAt" to FieldValue.serverTimestamp()
                        )
                        tx.set(ref, payload + pk.edunexa.app.activity.ActivityHistory.patch(savedDocuments.firstOrNull { it.id==documentId }?.data.orEmpty(),payload,com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in again"),adminName,"admin","Admin attendance override",student.fullName,now), SetOptions.merge())
                    }

                    Unit
                    }.addOnSuccessListener { onResult(Result.success(Unit)) }
                     .addOnFailureListener { onResult(Result.failure(IllegalStateException("Could not save Admin Override attendance. " + it.message.orEmpty(), it))) }
                    }
                }
        }
    }

    private fun attendanceDocId(
        sessionId: String,
        date: String,
        classId: String,
        sectionId: String,
        studentId: String
    ): String = listOf(sessionId, date, classId, sectionId, studentId)
        .joinToString("__") { cleanDocSegment(it) }

    private fun cleanDocSegment(value: String): String =
        value.trim()
            .replace("/", "_")
            .replace("\\", "_")
            .replace(Regex("\\s+"), "-")
            .take(120)

    private fun normalizeStatus(value: String): String = when (value.trim().uppercase(Locale.US)) {
        "PRESENT" -> "Present"
        "ABSENT" -> "Absent"
        "LEAVE" -> "Leave"
        "LATE" -> "Late"
        else -> "Unmarked"
    }

    private fun isoNow(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }

    private data class StudentScoped(
        val classId: String,
        val sectionId: String,
        val student: AttendanceStudent
    )
}



