package pk.edunexa.app.reports

import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.exams.ExamPolicy
import java.util.Locale

data class ParentReportData(val student: DiaryItem, val config: Map<String, Any?>, val settings: Map<String, Any?>, val attendance: List<DiaryItem>, val diaries: List<DiaryItem>, val checks: List<DiaryItem>, val tests: List<DiaryItem>, val marks: List<DiaryItem>, val fees: List<DiaryItem> = emptyList()) {
    val sessions get() = DiaryPolicy.maps(config["sessions"])
    val schoolName get() = settings["schoolName"]?.toString().orEmpty().ifBlank { "EduNexa" }
}
data class ParentReportSection(val title: String, val metrics: List<Pair<String,String>>, val headers: List<String>, val rows: List<List<String>>, val note: String)
data class ParentReport(val studentName: String, val details: String, val scope: String, val sections: List<ParentReportSection>)

object ParentReportPolicy {
    private fun newest(rows: List<DiaryItem>) = rows.maxByOrNull { it.text("lastUpdatedAt").ifBlank { it.text("updatedAt").ifBlank { it.text("checkedAt").ifBlank { it.text("markedAt") } } } }
    private fun number(value: Double) = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
    fun build(data: ParentReportData, sessionId: String, month: String, wholeSessionTests: Boolean = true): ParentReport {
        require(sessionId.isNotBlank()) { "Select an academic session." }
        require(month.isNotBlank()) { "Select a month." }
        val selected = data.sessions.firstOrNull { it["id"] == sessionId || it["name"] == sessionId }
        val aliases = setOf(sessionId, selected?.get("id")?.toString().orEmpty(), selected?.get("name")?.toString().orEmpty()).filter { it.isNotBlank() }.toSet()
        fun inSession(row: DiaryItem) = row.text("academicSessionId").ifBlank { row.text("academicSession") } in aliases
        fun inMonth(date: String) = ReportPolicy.dateMonth(date) == month
        fun member(row: DiaryItem) = (row.raw["studentIds"] as? List<*>)?.contains(data.student.id) == true
        val attendance = data.attendance.filter { it.text("studentId") == data.student.id && inSession(it) && inMonth(it.text("attendanceDate").ifBlank { it.text("date") }) }
            .groupBy { it.text("attendanceDate").ifBlank { it.text("date") } }.values.mapNotNull { newest(it) }.sortedBy { it.text("attendanceDate").ifBlank { it.text("date") } }
        fun count(status: String) = attendance.count { it.text("status").equals(status,true) }
        val lateCounts = data.settings["lateCountsAsPresent"] as? Boolean ?: true
        val excludeLeave = data.settings["excludeLeaveFromAttendance"] as? Boolean ?: true
        val rate = pk.edunexa.app.settings.AttendancePolicy.rate(count("Present"),count("Absent"),count("Late"),count("Leave"),lateCounts,excludeLeave)
        val attendanceSection = ParentReportSection("Monthly Attendance", listOf("Present" to count("Present").toString(), "Absent" to count("Absent").toString(), "Leave" to count("Leave").toString(), "Late" to count("Late").toString(), "Recorded Attendance" to (rate?.let { number(it)+"%" } ?: "—")), listOf("Date","Class / Section","Status","Follow-up / Arrival"), attendance.map { listOf(it.text("attendanceDate").ifBlank { it.text("date") }, "${it.text("className")} ${it.text("section")}", it.text("status"), listOf(it.text("followUpComment"),it.text("arrivalTime").takeIf { time -> time.isNotBlank() }?.let { time -> "Arrival $time PKT" }.orEmpty()).filter { value -> value.isNotBlank() }.joinToString(" • ")) }, "Only saved attendance is counted. Unsaved days are not absences. Late counts as present: $lateCounts; leave excluded from rate: $excludeLeave.")
        val checksByDiary = data.checks.groupBy { it.text("diaryId") }.mapValues { (_, records) -> newest(records)!! }
        val homework = data.diaries.filter { entry -> entry.text("homework").isNotBlank() && inSession(entry) && inMonth(entry.text("date")) && (member(entry) || DiaryPolicy.maps(checksByDiary[entry.id]?.raw?.get("records")).any { it["studentId"] == data.student.id }) }.sortedWith(compareBy<DiaryItem>{it.text("date")}.thenBy {it.text("subject")})
        val homeworkRows = homework.map { entry ->
            val record = DiaryPolicy.maps(checksByDiary[entry.id]?.raw?.get("records")).firstOrNull { it["studentId"] == data.student.id }
            val status = record?.get("status")?.toString()?.takeIf { it in DiaryPolicy.statuses } ?: "Not Checked"
            listOf(entry.text("date"), entry.text("subject"), entry.text("homework"), entry.text("dueDate"), status, record?.get("remarks")?.toString().orEmpty())
        }
        val days = homeworkRows.groupBy { it[0] }.values
        fun daily(statuses: List<String>): String = when {
            statuses.all { it == "Completed" } -> "Completed"
            statuses.any { it == "Not Done" } -> "Not Done"
            statuses.any { it == "Incomplete" } -> "Incomplete"
            statuses.any { it == "Not Checked" } -> "Not Checked"
            statuses.all { it == "Absent" } -> "Absent"
            else -> "Mixed"
        }
        val dayStatuses = days.map { daily(it.map { row -> row[4] }) }
        val homeworkSection = ParentReportSection("Monthly Homework", listOf("Homework Days" to days.size.toString(), "Completed Days" to dayStatuses.count { it == "Completed" }.toString(), "Not Done Days" to dayStatuses.count { it == "Not Done" }.toString(), "Incomplete Days" to dayStatuses.count { it == "Incomplete" }.toString(), "Unchecked Days" to dayStatuses.count { it == "Not Checked" }.toString(), "Absent / Mixed Days" to "${dayStatuses.count { it == "Absent" }} / ${dayStatuses.count { it == "Mixed" }}", "Assignments Completed" to "${homeworkRows.count { it[4] == "Completed" }} / ${homeworkRows.size}"), listOf("Assigned","Subject","Homework","Due","Check Status","Remarks"), homeworkRows, "Grouped by assigned date. A completed day requires every recorded assignment to be completed. Not Done and Incomplete take priority; otherwise an unchecked task keeps the day unchecked. Unknown checks are never counted as Not Done. Only frozen student rosters or saved checks identify assignments for this student.")
        val marksByTest = data.marks.filter { it.text("studentId") == data.student.id }.groupBy { it.text("testId") }.mapValues { (_, rows) -> newest(rows)!! }
        val tests = data.tests.filter { inSession(it) && (wholeSessionTests || inMonth(it.text("date"))) && it.text("status") != "Cancelled" && (member(it) || ((it.raw["studentIds"] as? List<*>) == null && marksByTest.containsKey(it.id))) }
        val testSections = tests.groupBy { ExamPolicy.typeLabel(it.text("assessmentType")) + it.text("boardStage").takeIf { stage -> stage.isNotBlank() }?.let { stage -> " / $stage" }.orEmpty() }.toSortedMap().map { (type, group) ->
            val rows = group.sortedWith(compareBy<DiaryItem>{it.text("subject")}.thenBy{it.text("date")}).map { test ->
                val mark = marksByTest[test.id]
                val total = (test.raw["totalMarks"] as? Number)?.toDouble()
                val score = (mark?.raw?.get("marksObtained") as? Number)?.toDouble()
                val valid = mark?.text("status") == "Present" && total != null && total.isFinite() && total > 0 && score != null && score.isFinite() && score in 0.0..total
                val status = when { mark == null -> "Not Recorded"; mark.text("status") in setOf("Absent","Exempt") -> mark.text("status"); valid -> "Present"; else -> "Not Recorded" }
                listOf(test.text("subject"), test.text("date"), test.text("title"), status, if(valid) number(score!!) else "—", total?.takeIf { it.isFinite() && it > 0 }?.let { number(it) } ?: "—", if(valid) number(score!! * 100 / total!!)+"%" else "—")
            }
            ParentReportSection(type, listOf("Tests" to rows.size.toString(), "Scored" to rows.count { it[3] == "Present" }.toString(), "Absent" to rows.count { it[3] == "Absent" }.toString(), "Not Recorded" to rows.count { it[3] == "Not Recorded" }.toString()), listOf("Subject","Date","Test","Status","Obtained","Total","Percent"), rows, "Test period: ${if(wholeSessionTests) "whole selected academic session" else month}. Only tests with a saved student roster or this student's saved marks are included. Pending, Absent and Exempt entries do not become zero marks.")
        }
        val examSections = testSections.ifEmpty { listOf(ParentReportSection("Tests & Results", emptyList(),listOf("Subject","Date","Test","Status","Obtained","Total","Percent"),emptyList(),"No linked test records for ${if(wholeSessionTests) "this academic session" else "this month and session"}.")) }
        val monthlyFees=data.fees.filter { it.text("studentId")==data.student.id && inSession(it) && it.text("month")==month }
        val feeSections=if(monthlyFees.isEmpty()) emptyList() else listOf(ParentReportSection("Monthly Fees & Funds",listOf("Billed" to number(monthlyFees.sumOf { pk.edunexa.app.fees.FeePolicy.amount(it.raw,"netPayable") }),"Paid" to number(monthlyFees.sumOf { pk.edunexa.app.fees.FeePolicy.paid(it.raw) }),"Outstanding" to number(monthlyFees.sumOf { pk.edunexa.app.fees.FeePolicy.balance(it.raw) })),listOf("Charge","Month","Bill","Paid","Due","Status"),monthlyFees.map { listOf(it.text("feeType"),it.text("month"),number(pk.edunexa.app.fees.FeePolicy.amount(it.raw,"netPayable")),number(pk.edunexa.app.fees.FeePolicy.paid(it.raw)),number(pk.edunexa.app.fees.FeePolicy.balance(it.raw)),pk.edunexa.app.fees.FeePolicy.status(it.raw)) },"Saved bills for the selected month and session. Paid includes installments received against these bills; this is not a cash-received-by-date ledger."))
        val comments=data.student.text("remarks").takeIf { it.isNotBlank() }?.let { listOf(ParentReportSection("Profile Remarks",emptyList(),listOf("Remarks"),listOf(listOf(it)),"Saved student profile remarks. Assignment-specific teacher remarks appear with homework.")) }.orEmpty()
        val student = data.student
        return ParentReport(student.text("fullName"), listOf("Admission: ${student.text("admissionNo")}","Roll: ${student.text("rollNo")}", "Current class: ${student.text("className")} ${student.text("section")}","Parent: ${student.text("fatherName")}").joinToString(" • "), "${selected?.get("name") ?: sessionId} • Attendance / Homework: $month • Tests: ${if(wholeSessionTests) "Whole Session" else month}", listOf(attendanceSection,homeworkSection)+examSections+feeSections+comments)
    }
}
