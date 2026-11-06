package pk.edunexa.app.reports

import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.exams.ExamPolicy
import java.text.NumberFormat
import java.util.Locale

private fun Map<String, Any?>.text(key: String) = this[key]?.toString().orEmpty()
data class ReportFilter(val sessionId: String, val classId: String = "", val sectionId: String = "", val month: String = FeePolicy.month(), val search: String = "")
data class ReportData(val config: Map<String, Any?>, val records: List<DiaryItem>, val related: List<DiaryItem>, val schoolName: String, val settings: Map<String, Any?> = emptyMap()) {
    fun options(key: String) = DiaryPolicy.maps(config[key])
    val currentSession get() = options("sessions").firstOrNull { it["isCurrent"] == true }.orEmpty()
}
data class ReportDocument(val title: String, val scope: String, val metrics: List<Pair<String, String>>, val headers: List<String>, val rows: List<List<String>>, val notes: List<String>)

object ReportPolicy {
    val categories = listOf("Students", "Attendance", "Fees", "Exams", "Faculty")
    fun dateMonth(date: String): String = runCatching {
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false; timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi") }
        val parsed = f.parse(date) ?: error("Invalid date")
        java.text.SimpleDateFormat("MMMM yyyy", Locale.US).apply { timeZone = f.timeZone }.format(parsed)
    }.getOrDefault("")
    fun aliases(data: ReportData, sessionId: String): Set<String> {
        val academic = data.options("sessions").firstOrNull { it["id"] == sessionId || it["name"] == sessionId }
        return setOf(sessionId, academic?.text("id").orEmpty(), academic?.text("name").orEmpty()).filter { it.isNotBlank() }.toSet()
    }
    fun scoped(row: DiaryItem, data: ReportData, filter: ReportFilter): Boolean {
        if(filter.sessionId != "__all__" && row.text("academicSessionId").ifBlank { row.text("academicSession") } !in aliases(data, filter.sessionId)) return false
        val cls = data.options("classes").firstOrNull { it["id"] == filter.classId }
        val sec = data.options("sections").firstOrNull { it["id"] == filter.sectionId }
        val classMatches = filter.classId.isBlank() || (if(row.text("classId").isNotBlank()) row.text("classId") == filter.classId else cls != null && row.text("className") == cls.text("name"))
        val sectionMatches = filter.sectionId.isBlank() || (if(row.text("sectionId").isNotBlank()) row.text("sectionId") == filter.sectionId else sec != null && row.text("section") == sec.text("name"))
        return classMatches && sectionMatches
    }
    fun money(amount: Double) = "Rs. " + NumberFormat.getNumberInstance(Locale.US).format(amount)
    private fun matches(row: DiaryItem, query: String, fields: List<String>): Boolean = query.isBlank() || fields.any { row.text(it).contains(query.trim(), true) }
    fun build(category: String, data: ReportData, filter: ReportFilter): ReportDocument {
        require(category in categories)
        val monthUsed = category in setOf("Attendance", "Fees", "Exams")
        val scope = if(category == "Faculty") "School faculty • ${FeePolicy.today()}" else listOf(
            if(filter.sessionId == "__all__") "All Sessions" else data.options("sessions").firstOrNull { it["id"] == filter.sessionId }?.text("name") ?: filter.sessionId,
            data.options("classes").firstOrNull { it["id"] == filter.classId }?.text("name") ?: "All Classes",
            data.options("sections").firstOrNull { it["id"] == filter.sectionId }?.text("name") ?: "All Sections",
            if(monthUsed) filter.month.ifBlank { "All Months" } else ""
        ).filter { it.isNotBlank() }.joinToString(" • ") + if(filter.search.isNotBlank()) " • Search: ${filter.search.trim()}" else ""
        val base = if(category == "Faculty") data.records else data.records.filter { scoped(it, data, filter) }
        val common = if(category == "Faculty") listOf("Faculty is school-wide; academic session, class and month filters do not apply.") else if(filter.sessionId == "__all__") listOf("All saved student profiles are included, including records without an academic session. Use Academic Session to select a specific enrollment session.") else listOf("Only saved records matching the selected academic session and class scope are included. Missing session records are excluded.")
        return when(category) {
            "Students" -> {
                val students = base.filter { matches(it, filter.search, listOf("fullName", "admissionNo", "rollNo", "fatherName")) }.sortedBy { it.text("fullName") }
                val fees = data.related
                val rows = students.map { student ->
                    val fee = FeePolicy.displaySnapshot(fees, student.id, if(filter.sessionId == "__all__") aliases(data, student.text("academicSessionId").ifBlank { student.text("academicSession") }) else aliases(data, filter.sessionId))
                    listOf(student.text("fullName"), student.text("admissionNo"), "${student.text("className")} ${student.text("section")}", student.text("rollNo"), student.text("status"), fee.first, fee.second?.let { money(it) } ?: "—")
                }
                ReportDocument("Student Report", scope, listOf("Students" to students.size.toString(), "Active" to students.count { it.text("status") == "Active" }.toString(), "Boys" to students.count { it.text("gender") == "Male" }.toString(), "Girls" to students.count { it.text("gender") == "Female" }.toString()), listOf("Student", "Admission", "Class / Section", "Roll", "Status", "Tuition Status", "Saved Tuition Due"), rows, common + "Amounts cover saved current and earlier tuition bills; funds are separate. Profiles without an enrollment session show saved tuition due across sessions and are marked Session not set. A dash means no verified bill amount, not a zero balance. Enrollment has no month filter.")
            }
            "Attendance" -> {
                // One saved entry per student/day/class/section; retain the most recently updated duplicate.
                val entries = base.filter { filter.month.isBlank() || dateMonth(it.text("attendanceDate").ifBlank { it.text("date") }) == filter.month }
                    .groupBy { listOf(it.text("studentId").ifBlank { it.id }, it.text("attendanceDate").ifBlank { it.text("date") }, it.text("classId").ifBlank { it.text("className") }, it.text("sectionId").ifBlank { it.text("section") }) }
                    .values.map { rows -> rows.maxBy { it.text("lastUpdatedAt").ifBlank { it.text("markedAt") } } }
                    .filter { matches(it, filter.search, listOf("studentName", "admissionNo", "rollNo", "status")) }.sortedWith(compareByDescending<DiaryItem> { it.text("attendanceDate").ifBlank { it.text("date") } }.thenBy { it.text("studentName") })
                fun count(value: String) = entries.count { it.text("status").equals(value, true) }
                val marked = listOf("Present", "Absent", "Late", "Leave").sumOf { count(it) }
                val lateCounts = data.settings["lateCountsAsPresent"] as? Boolean ?: true
                val excludeLeave = data.settings["excludeLeaveFromAttendance"] as? Boolean ?: true
                val minimum = (data.settings["attendanceMinimumPercent"] as? Number)?.toDouble() ?: 75.0
                val calculated = pk.edunexa.app.settings.AttendancePolicy.rate(count("Present"), count("Absent"), count("Late"), count("Leave"), lateCounts, excludeLeave)
                val rate = calculated?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"
                val belowMinimum = entries.groupBy { it.text("studentId").ifBlank { it.id } }.values.count { studentRows ->
                    fun total(status: String) = studentRows.count { it.text("status").equals(status, true) }
                    pk.edunexa.app.settings.AttendancePolicy.rate(total("Present"), total("Absent"), total("Late"), total("Leave"), lateCounts, excludeLeave)?.let { it < minimum } == true
                }
                ReportDocument("Attendance Report", scope, listOf("Marked Entries" to marked.toString(), "Present / Late" to "${count("Present")} / ${count("Late")}", "Absent / Leave" to "${count("Absent")} / ${count("Leave")}", "Recorded Attendance Rate" to rate, "Minimum Required" to "$minimum%", "Students Below Minimum" to belowMinimum.toString()), listOf("Date", "Student", "Class / Section", "Roll", "Status", "Follow-up"), entries.map { listOf(it.text("attendanceDate").ifBlank { it.text("date") }, it.text("studentName"), "${it.text("className")} ${it.text("section")}", it.text("rollNo"), it.text("status"), it.text("followUpComment")) }, common + "Saved attendance policy: Late counts as present = $lateCounts; Leave excluded from denominator = $excludeLeave. Minimum = $minimum%. Unmarked or unsaved days are not counted as absent. Counts represent student-days.")
            }
            "Fees" -> {
                val fees = base.filter { row ->
                    val monthly = row.text("feeType") == FeePolicy.TUITION
                    val period = !monthly || filter.month.isBlank() || row.text("month") == filter.month || (FeePolicy.balance(row.raw) > 0 && runCatching { FeePolicy.monthKey(row.text("month")) < FeePolicy.monthKey(filter.month) }.getOrDefault(false))
                    period && matches(row, filter.search, listOf("studentName", "admissionNo", "rollNo", "voucherNo", "feeType"))
                }.sortedBy { it.text("studentName") }
                val tuition = fees.filter { it.text("feeType") == FeePolicy.TUITION }
                val funds = fees.filter { it.text("feeType") != FeePolicy.TUITION }
                ReportDocument("Fee Report", scope, listOf("Tuition Billed" to money(tuition.sumOf { FeePolicy.amount(it.raw,"netPayable") }), "Tuition Paid / Due" to "${money(tuition.sumOf { FeePolicy.paid(it.raw) })} / ${money(tuition.sumOf { FeePolicy.balance(it.raw) })}", "Funds Paid" to money(funds.sumOf { FeePolicy.paid(it.raw) }), "Funds Outstanding" to money(funds.sumOf { FeePolicy.balance(it.raw) })), listOf("Student", "Class / Section", "Charge / Month", "Bill", "Paid", "Outstanding", "Status"), fees.map { listOf(it.text("studentName"), "${it.text("className")} ${it.text("section")}", "${it.text("feeType")} / ${it.text("month")}", money(FeePolicy.amount(it.raw,"netPayable")), money(FeePolicy.paid(it.raw)), money(FeePolicy.balance(it.raw)), FeePolicy.status(it.raw)) }, common + "Tuition includes the chosen month and earlier outstanding bills. Funds cover the whole selected academic session. Paid totals are lifetime payments against these bills, not payments received during the month.")
            }
            "Exams" -> {
                val tests = base.filter { (filter.month.isBlank() || dateMonth(it.text("date")) == filter.month) && matches(it, filter.search, listOf("title", "subject", "teacherName", "assessmentType", "boardStage")) }.sortedByDescending { it.text("date") }
                var marked = 0; var absent = 0
                val rows = tests.map { test ->
                    val ids = (test.raw["studentIds"] as? List<*>)?.filterIsInstance<String>()?.toSet()
                    val records = data.related.filter { it.text("testId") == test.id && (ids == null || it.text("studentId") in ids) }.groupBy { it.text("studentId") }.values.map { duplicate -> duplicate.maxBy { it.text("updatedAt").ifBlank { it.text("createdAt") } } }
                    val total = (test.raw["totalMarks"] as? Number)?.toDouble() ?: 0.0
                    val scored = records.filter { it.text("status") == "Present" && (it.raw["marksObtained"] as? Number)?.toDouble()?.let { score -> score.isFinite() && total > 0 && score in 0.0..total } == true }
                    val completed = scored.size + records.count { it.text("status") in setOf("Absent", "Exempt") }
                    marked += completed; absent += records.count { it.text("status") == "Absent" }
                    val average = if(scored.isEmpty()) "—" else String.format(Locale.US, "%.1f%%", scored.map { (it.raw["marksObtained"] as Number).toDouble() * 100 / total }.average())
                    val type = ExamPolicy.typeLabel(test.text("assessmentType")) + test.text("boardStage").takeIf { it.isNotBlank() }?.let { " / $it" }.orEmpty()
                    listOf(test.text("date"), test.text("title"), "${test.text("className")} ${test.text("section")}", test.text("subject"), type, if(ids == null) "$completed / roster unavailable" else "$completed / ${ids.size}", average)
                }
                ReportDocument("Tests & Results Report", scope, listOf("Tests" to tests.size.toString(), "Completed Mark Entries" to marked.toString(), "Absent Entries" to absent.toString(), "Subjects" to tests.map { it.text("subjectId") }.filter { it.isNotBlank() }.distinct().size.toString()), listOf("Date", "Test", "Class / Section", "Subject", "Type / Stage", "Marks Entered", "Scored Average"), rows, common + "Average uses only valid scored Present entries. Pending, Absent and Exempt records are not converted to zero marks. Admin reports are view-only.")
            }
            else -> {
                val teachers = base.filter { matches(it, filter.search, listOf("name", "employeeId", "qualification", "email", "phone")) }.sortedBy { it.text("name") }
                ReportDocument("Faculty Report", scope + if(filter.search.isNotBlank()) " • Search: ${filter.search.trim()}" else "", listOf("Teachers" to teachers.size.toString(), "Active" to teachers.count { it.text("status") == "Active" }.toString(), "Inactive" to teachers.count { it.text("status") == "Inactive" }.toString()), listOf("Teacher", "Employee ID", "Qualification", "Phone", "Email", "Status"), teachers.map { listOf(it.text("name"), it.text("employeeId"), it.text("qualification"), it.text("phone"), it.text("email"), it.text("status")) }, common)
            }
        }
    }
}


