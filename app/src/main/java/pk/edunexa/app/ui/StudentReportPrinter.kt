package pk.edunexa.app.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.exams.ExamPolicy
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.students.StudentPortalData
import pk.edunexa.app.students.StudentPortalPolicy

object StudentReportPrinter {
    private fun esc(value: String) = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    internal fun html(d: StudentPortalData, month: String, logo: String=""): String {
        fun table(headers: List<String>, rows: List<List<String>>): String {
            if(rows.isEmpty()) return "<p class='muted'>No saved records.</p>"
            return "<table><thead><tr>" + headers.joinToString("") { "<th>" + esc(it) + "</th>" } + "</tr></thead><tbody>" + rows.joinToString("") { cells -> "<tr>" + cells.joinToString("") { "<td>" + esc(it) + "</td>" } + "</tr>" } + "</tbody></table>"
        }
        val attendance = StudentPortalPolicy.attendance(d.attendance.filter { d.inSession(it) },month)
        val diary = d.currentDiary.filter { it.text("date").startsWith(month + "-") && it.text("homework").isNotBlank() }
        val results = d.currentResults.sortedWith(compareBy({ it.text("subject") },{ it.text("publishedAt") })).map { row ->
            val test = d.tests.firstOrNull { it.id == row.text("testId") }
            val named = DiaryItem(row.id,row.raw + mapOf("totalMarks" to (row.raw["totalMarks"] ?: test?.raw?.get("totalMarks"))))
            listOf(row.text("subject"),ExamPolicy.typeLabel(row.text("assessmentType").ifBlank { test?.text("assessmentType").orEmpty() }),row.text("testTitle"),test?.text("date").orEmpty(),StudentPortalPolicy.resultScore(named))
        }
        val student = d.student
        return """<!doctype html><html><head><meta charset='utf-8'><style>@page{size:A4 portrait;margin:14mm}body{font-family:Arial,sans-serif;color:#101827;font-size:11px}h1{text-align:center;font-size:20px}h2{font-size:15px;color:#946c23;margin:20px 0 8px}p{line-height:1.6}table{width:100%;border-collapse:collapse;table-layout:fixed;margin:10px 0}th,td{border:1px solid #cbd5e1;padding:7px;text-align:left;overflow-wrap:anywhere}th{background:#eef2f7}thead{display:table-header-group}tr{break-inside:avoid}.muted{color:#64748b}footer{margin-top:24px}</style></head><body>${if(logo.isNotBlank()) "<div style='text-align:center'><img style='width:55px;height:55px;object-fit:contain' src='data:image/png;base64,${esc(logo)}'></div>" else ""}<h1>${esc(d.schoolName)}</h1><h2 style='text-align:center'>Complete Student Report</h2><p><b>${esc(student.text("fullName"))}</b><br>Admission: ${esc(student.text("admissionNo"))} • Roll: ${esc(student.text("rollNo"))}<br>Class: ${esc(student.text("className"))} • Section: ${esc(student.text("section"))}<br>Session: ${esc(d.sessionLabel)} • Attendance/Homework Month: ${esc(month)}</p><h2>Monthly Attendance</h2><p>Recorded Days: ${attendance.size} • Attendance: ${esc(StudentPortalPolicy.attendancePercent(attendance))} • Present: ${attendance.count { it.text("status") == "Present" }} • Absent: ${attendance.count { it.text("status") == "Absent" }} • Leave: ${attendance.count { it.text("status") == "Leave" }} • Late: ${attendance.count { it.text("status") == "Late" }}</p>${table(listOf("Date","Status"),attendance.map { listOf(it.text("attendanceDate"),it.text("status")) })}<h2>Monthly Homework Checks</h2>${table(listOf("Date","Subject","Homework","Teacher Check"),diary.map { listOf(it.text("date"),it.text("subject"),it.text("homework"),d.homeworkStatus(it)) })}<h2>Subject-wise Published Results</h2>${table(listOf("Subject","Test Type","Test","Date","Marks"),results)}<h2>Saved Fees & Funds</h2>${table(listOf("Charge","Month","Bill","Paid","Due"),d.fees.filter { d.inSession(it) && it.text("month")==pk.edunexa.app.reports.ReportPolicy.dateMonth(month+"-01") }.map { listOf(it.text("feeType"),it.text("month"),StudentPortalPolicy.number(FeePolicy.amount(it.raw,"netPayable")),StudentPortalPolicy.number(FeePolicy.paid(it.raw)),StudentPortalPolicy.number(FeePolicy.balance(it.raw))) })}<h2>Profile Remarks</h2><p>${esc(student.text("remarks").ifBlank { "No saved remarks." })}</p><footer class='muted'>Generated: ${FeePolicy.today()} • Saved school records only. Unpublished marks are excluded.</footer></body></html>"""
    }
    fun print(context: Context, data: StudentPortalData, month: String,schoolId:String?=null) {
        val web = WebView(context)
        web.webViewClient = object : WebViewClient() {
            private var opened = false
            override fun onPageFinished(view: WebView,url: String?) {
                if(opened) return
                opened = true
                (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("EduNexa-Student-Report",view.createPrintDocumentAdapter("Complete Student Report"),PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asPortrait()).build())
            }
        }
        web.loadDataWithBaseURL(null,html(data,month,cachedSchoolLogo(context,schoolId)),"text/html","UTF-8",null)
    }
}
