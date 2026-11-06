package pk.edunexa.app.dashboard

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import pk.edunexa.app.academic.AcademicSessionPolicy

data class AttendanceSummary(
    val present: Int = 0,
    val absent: Int = 0,
    val leave: Int = 0,
    val late: Int = 0,
    val recorded: Int = 0,
    val presentPercent: Int = 0
)

data class FeeSummary(
    val month: String,
    val totalBilled: Double = 0.0,
    val totalCollected: Double = 0.0,
    val totalPending: Double = 0.0,
    val totalOverdue: Double = 0.0,
    val recoveryRatio: Int = 0,
    val hasData: Boolean = false,
    val fundsCollected: Double = 0.0,
    val fundsPending: Double = 0.0
)

data class AdminDashboardData(
    val schoolName: String,
    val campusName: String,
    val activeSessionId: String,
    val activeSessionName: String,
    val studentsTotal: Int,
    val studentsActive: Int,
    val studentsOnLeave: Int,
    val activeTeachers: Int,
    val activeClasses: Int,
    val activeSections: Int,
    val activeSubjects: Int,
    val attendance: AttendanceSummary,
    val fees: FeeSummary,
    val loadedAt: String
)

class AdminDashboardRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun load(
        schoolId: String,
        onResult: (Result<AdminDashboardData>) -> Unit
    ) {
        if (schoolId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("The school ID is missing.")))
            return
        }

        val today = pk.edunexa.app.fees.FeePolicy.today()
        val schoolRef = db.collection("schools").document(schoolId)

        val schoolTask = schoolRef.get()
        val academicTask = schoolRef.collection("academicConfig").document("current").get()
        val studentsTask = schoolRef.collection("students").get()
        val teachersTask = schoolRef.collection("teachers").get()
        val attendanceTask = schoolRef.collection("attendance")
            .whereEqualTo("attendanceDate", today)
            .get()
        val feesTask = schoolRef.collection("fees").get()

        val tasks: List<Task<*>> = listOf(
            schoolTask,
            academicTask,
            studentsTask,
            teachersTask,
            attendanceTask,
            feesTask
        )

        Tasks.whenAllComplete(tasks).addOnCompleteListener {
            val failure = tasks.firstOrNull { !it.isSuccessful }?.exception
            if (failure != null) {
                onResult(
                    Result.failure(
                        IllegalStateException(firebaseMessage(failure), failure)
                    )
                )
                return@addOnCompleteListener
            }

            try {
                val school = schoolTask.result
                val academic = academicTask.result
                val students = studentsTask.result
                val teachers = teachersTask.result
                val attendance = attendanceTask.result
                val fees = feesTask.result

                val sessions = academic.mapList("sessions")
                val activeSession = sessions.firstOrNull { it.bool("isCurrent") } ?:
                    sessions.firstOrNull {
                        it.string("status").equals("Active", ignoreCase = true)
                    }

                val activeSessionId = activeSession?.string("id").orEmpty()
                val activeSessionName = activeSession?.string("name")
                    .orEmpty()
                    .ifBlank { activeSessionId.ifBlank { "Not configured" } }

                val aliases = listOf(activeSessionId, activeSessionName.takeUnless { it == "Not configured" }.orEmpty())
                val scopedStudents = students.documents.filter { it.getBoolean("isDeleted") != true }.filter { student ->
                    AcademicSessionPolicy.matches(student.getString("academicSessionId").orEmpty().ifBlank { student.getString("academicSession").orEmpty() }, aliases, allowMissing = true)
                }

                val studentTotal = scopedStudents.size
                val activeStudents = scopedStudents.count {
                    it.getString("status").equals("Active", ignoreCase = true)
                }
                val onLeaveStudents = scopedStudents.count {
                    it.getString("status").equals("On Leave", ignoreCase = true)
                }

                val activeTeachers = teachers.documents.count {
                    it.getString("status").equals("Active", ignoreCase = true)
                }

                val classes = academic.mapList("classes")
                val sections = academic.mapList("sections")
                val subjects = academic.mapList("subjects")

                val activeClasses = classes.count { it.bool("isActive") }
                val activeSections = sections.count { it.bool("isActive") }
                val activeSubjects = subjects.count { it.bool("isActive") }

                val settings = school.get("settings") as? Map<*, *>
                val lateCountsAsPresent =
                    settings?.get("lateCountsAsPresent") as? Boolean ?: true
                val excludeLeave =
                    settings?.get("excludeLeaveFromAttendance") as? Boolean ?: true

                val scopedAttendance = attendance.documents.filter { record ->
                    AcademicSessionPolicy.matches(record.getString("academicSessionId").orEmpty().ifBlank { record.getString("academicSession").orEmpty() }, aliases) &&
                        record.getString("studentId").orEmpty().isNotBlank()
                }.groupBy { it.getString("studentId") }.values.map { rows ->
                    rows.maxWithOrNull(compareBy<DocumentSnapshot>({ it.getString("lastUpdatedAt").orEmpty().ifBlank { it.getString("updatedAt").orEmpty().ifBlank { it.getString("markedAt").orEmpty() } } }, { it.id }))!!
                }.filter { it.getString("status").orEmpty().lowercase(Locale.US) in setOf("present", "absent", "leave", "late") }

                val presentOnly = scopedAttendance.count {
                    it.getString("status").equals("PRESENT", ignoreCase = true) ||
                        it.getString("status").equals("Present", ignoreCase = true)
                }
                val absent = scopedAttendance.count {
                    it.getString("status").equals("ABSENT", ignoreCase = true) ||
                        it.getString("status").equals("Absent", ignoreCase = true)
                }
                val leave = scopedAttendance.count {
                    it.getString("status").equals("LEAVE", ignoreCase = true) ||
                        it.getString("status").equals("Leave", ignoreCase = true)
                }
                val late = scopedAttendance.count {
                    it.getString("status").equals("LATE", ignoreCase = true) ||
                        it.getString("status").equals("Late", ignoreCase = true)
                }
                val present = presentOnly + if (lateCountsAsPresent) late else 0
                val presentPercent = pk.edunexa.app.settings.AttendancePolicy.rate(presentOnly, absent, late, leave, lateCountsAsPresent, excludeLeave)?.toInt() ?: 0

                val attendanceSummary = AttendanceSummary(
                    present = present,
                    absent = absent,
                    leave = leave,
                    late = late,
                    recorded = scopedAttendance.size,
                    presentPercent = presentPercent
                )

                val feeSummary = buildFeeSummary(fees, activeSessionId, activeSessionName, students.documents.filter { it.getBoolean("isDeleted") != true }.map { it.id }.toSet())

                val settingsMap = school.get("settings") as? Map<*, *>
                val schoolName = settingsMap?.get("schoolName")
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "EduNexa" }
                val campusName = settingsMap?.get("campusName")
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "School Portal" }

                val loadedAt = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Karachi") }.format(Date())

                onResult(
                    Result.success(
                        AdminDashboardData(
                            schoolName = schoolName,
                            campusName = campusName,
                            activeSessionId = activeSessionId,
                            activeSessionName = activeSessionName,
                            studentsTotal = studentTotal,
                            studentsActive = activeStudents,
                            studentsOnLeave = onLeaveStudents,
                            activeTeachers = activeTeachers,
                            activeClasses = activeClasses,
                            activeSections = activeSections,
                            activeSubjects = activeSubjects,
                            attendance = attendanceSummary,
                            fees = feeSummary,
                            loadedAt = loadedAt
                        )
                    )
                )
            } catch (error: Exception) {
                onResult(
                    Result.failure(
                        IllegalStateException(
                            "Could not load dashboard data.",
                            error
                        )
                    )
                )
            }
        }
    }

    private fun buildFeeSummary(
        snapshot: QuerySnapshot,
        activeSessionId: String,
        activeSessionName: String,
        currentStudentIds: Set<String>
    ): FeeSummary {
        val currentMonth = pk.edunexa.app.fees.FeePolicy.month()
        val all = snapshot.documents.filter { it.getString("studentId") in currentStudentIds }.filter { AcademicSessionPolicy.matches(it.getString("academicSessionId").orEmpty().ifBlank { it.getString("academicSession").orEmpty() }, listOf(activeSessionId, activeSessionName.takeUnless { it == "Not configured" }.orEmpty())) }.map { it.data.orEmpty() }
        val tuition = all.filter { it["feeType"] == pk.edunexa.app.fees.FeePolicy.TUITION }
        val current = tuition.filter { it["month"] == currentMonth }
        val billed = current.sumOf { pk.edunexa.app.fees.FeePolicy.amount(it, "netPayable") }
        val collected = current.sumOf { pk.edunexa.app.fees.FeePolicy.paid(it) }
        val overdueCurrent = current.filter { it["dueDate"].toString() < pk.edunexa.app.fees.FeePolicy.today() }.sumOf { pk.edunexa.app.fees.FeePolicy.balance(it) }
        val previous = tuition.filter { monthBefore(it["month"].toString(), currentMonth) }.sumOf { pk.edunexa.app.fees.FeePolicy.balance(it) }
        val funds = all.filter { it["feeType"] != pk.edunexa.app.fees.FeePolicy.TUITION }
        return FeeSummary(month = currentMonth, totalBilled = billed, totalCollected = collected,
            totalPending = (current.sumOf { pk.edunexa.app.fees.FeePolicy.balance(it) } - overdueCurrent).coerceAtLeast(0.0), totalOverdue = overdueCurrent + previous,
            recoveryRatio = if (billed > 0) ((collected / billed) * 100).toInt().coerceIn(0, 100) else 0, hasData = all.isNotEmpty(),
            fundsCollected = funds.sumOf { pk.edunexa.app.fees.FeePolicy.paid(it) }, fundsPending = funds.sumOf { pk.edunexa.app.fees.FeePolicy.balance(it) })
    }

    private fun monthBefore(value: String, target: String): Boolean {
        if (value.isBlank()) return false
        return try {
            val formatter = SimpleDateFormat("MMMM yyyy", Locale.US)
            val left = formatter.parse(value) ?: return false
            val right = formatter.parse(target) ?: return false
            left.before(right)
        } catch (_: Exception) {
            false
        }
    }

    private fun DocumentSnapshot.mapList(field: String): List<Map<String, Any?>> {
        val raw = get(field) as? List<*> ?: return emptyList()
        return raw.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            map.entries.associate { entry ->
                entry.key.toString() to entry.value
            }
        }
    }

    private fun Map<String, Any?>.string(field: String): String =
        this[field]?.toString()?.trim().orEmpty()

    private fun Map<String, Any?>.bool(field: String): Boolean =
        this[field] as? Boolean ?: false

    private fun DocumentSnapshot.number(field: String): Double =
        (get(field) as? Number)?.toDouble() ?: 0.0

    private fun firebaseMessage(error: Exception): String {
        val message = error.message.orEmpty()
        return if (message.contains("PERMISSION_DENIED", ignoreCase = true)) {
            "Dashboard access was denied. Check your Admin permissions."
        } else {
            "Could not load the live dashboard. Check your internet connection and try again."
        }
    }
}



