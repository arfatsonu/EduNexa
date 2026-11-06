package pk.edunexa.app.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.exams.ExamPolicy
import pk.edunexa.app.exams.GazettePolicy

object ExamPrinter {
    fun print(context: Context, schoolName: String, tests: List<DiaryItem>, marks: List<DiaryItem>,schoolId:String?=null) {
        fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun num(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
        val groups = tests.filter { it.text("status") != "Cancelled" }.groupBy { listOf(it.text("academicSessionId"), it.text("classId"), it.text("sectionId")) }
        val content = groups.values.joinToString("") { exams ->
            val examIds = exams.map { it.id }.toSet()
            val rows = marks.filter { mark ->
                mark.text("testId") in examIds && mark.text("studentId").isNotBlank() && exams.firstOrNull { it.id == mark.text("testId") }?.let { test ->
                    val roster = (test.raw["studentIds"] as? List<*>)?.filterIsInstance<String>()
                    roster == null || mark.text("studentId") in roster
                } == true
            }
            val ids = (exams.flatMap { (it.raw["studentIds"] as? List<*>)?.filterIsInstance<String>().orEmpty() } + rows.map { it.text("studentId") }).distinct()
            val body = ids.sortedWith(compareBy({ rows.firstOrNull { r -> r.text("studentId") == it }?.text("rollNo")?.toIntOrNull() ?: Int.MAX_VALUE }, { it })).joinToString("") { id ->
                val own = rows.filter { it.text("studentId") == id }; val student = GazettePolicy.latest(own)
                var pending = false; var total = 0.0; var obtained = 0.0; var failed = false
                val cells = exams.joinToString("") { test ->
                    val cell = GazettePolicy.cell(test, id, own)
                    pending = pending || cell.pending
                    failed = failed || cell.failed
                    total += cell.total
                    obtained += cell.obtained
                    val label = cell.label
                    "<td>${esc(label)}</td>"
                }
                val percent = if (!pending && total > 0) String.format(java.util.Locale.US, "%.2f%%", obtained * 100 / total) else "—"
                val result = if (pending) "Pending" else if (total == 0.0) "Exempt" else if (failed) "Fail" else "Pass"
                "<tr><td>${esc(student?.text("rollNo").orEmpty())}</td><td>${esc(student?.text("studentName")?.ifBlank { id } ?: id)}</td>$cells<td>${if (pending) "Pending" else num(obtained) + "/" + num(total)}</td><td>$percent</td><td>$result</td></tr>"
            }
            val first = exams.first()
            "<section>${PrintDesign.logo(cachedSchoolLogo(context,schoolId))}<h1>${esc(schoolName)}</h1><h2>Result Gazette</h2><p>${esc(first.text("academicSession"))} • ${esc(first.text("className"))} • ${esc(first.text("section"))}</p><table><thead><tr><th>Roll</th><th>Student</th>${exams.joinToString("") { "<th>${esc(it.text("subject"))}<br>${esc(it.text("title"))}<br>${esc(ExamPolicy.typeLabel(it.text("assessmentType")))}${if (it.text("assessmentType") == "Board Test Session") "<br>" + esc(it.text("boardStage")) else ""}<br>${esc(it.text("date"))}</th>" }}<th>Total</th><th>%</th><th>Result</th></tr></thead><tbody>$body</tbody></table><p>N/A = student outside the saved test roster. Pending = marks missing or invalid. Exempt subjects are excluded from totals. Absent subjects count toward total marks.</p><footer>Examiner: ____________________ &nbsp;&nbsp; Principal: ____________________</footer></section>"
        }
        val html = "<html><head><meta charset='utf-8'><style>${PrintDesign.css}@page{size:A4 landscape;margin:12mm}body{font-family:sans-serif;color:#0A0E17;font-size:10px}h1{font-size:18px}h2{font-size:15px}table{width:100%;border-collapse:collapse}td,th{border:1px solid #64748B;padding:6px;text-align:center;overflow-wrap:anywhere}thead{display:table-header-group}tr{break-inside:avoid}section{break-after:page}section:last-child{break-after:auto}footer{margin-top:30px}</style></head><body>$content</body></html>"
        val web = WebView(context);web.settings.javaScriptEnabled=false
        web.webViewClient = object : WebViewClient() { private var opened = false; override fun onPageFinished(view: WebView, url: String?) {
            if (opened) return
            opened = true
            (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("EduNexa-Result-Gazette", view.createPrintDocumentAdapter("Result Gazette"), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape()).build())
        } }
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
