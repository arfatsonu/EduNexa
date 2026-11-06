package pk.edunexa.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import pk.edunexa.app.R
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.exams.ExamPolicy
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

object DateSheetPrinter {
    private fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    internal fun html(schoolName: String, sheet: DiaryItem, logo: String = ""): String {
        val rows = DiaryPolicy.maps(sheet.raw["datesheetEntries"]).sortedWith(compareBy({ it["date"].toString() }, { it["startTime"].toString() }))
        fun time(value: String) = runCatching {
            SimpleDateFormat("HH:mm", Locale.US).apply { isLenient = false }.parse(value)?.let { SimpleDateFormat("h:mm a", Locale.US).format(it) }
        }.getOrNull() ?: value
        val table = rows.mapIndexed { index, row ->
            val date = row["date"]?.toString().orEmpty()
            val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(date) }.getOrNull()
            val day = parsed?.let { SimpleDateFormat("EEEE", Locale.US).format(it) }.orEmpty()
            val label = parsed?.let { SimpleDateFormat("dd MMM yyyy", Locale.US).format(it) } ?: date
            "<tr><td>${index + 1}</td><td>${esc(label)}</td><td>${esc(day)}</td><td>${esc(row["subject"]?.toString().orEmpty())}</td><td>${esc(time(row["startTime"]?.toString().orEmpty()))} – ${esc(time(row["endTime"]?.toString().orEmpty()))}</td></tr>"
        }.joinToString("")
        val image = if (logo.isBlank()) "" else "<img class='logo' src='data:image/png;base64,${esc(logo)}' alt='School logo'>"
        return """<!doctype html><html><head><meta charset='utf-8'><style>
            @page{size:A4 portrait;margin:12mm}
            *{box-sizing:border-box}body{font-family:Arial,sans-serif;color:#101827;font-size:12px;margin:0}
            header{text-align:center;border-bottom:2px solid #b88624;padding-bottom:12px}
            .logo{width:52px;height:52px;object-fit:contain}h1{font-size:20px;margin:6px 0}
            h2{font-size:16px;color:#946c23;margin:8px 0}.scope{line-height:1.7;margin:10px 0;text-align:center}
            table{width:100%;border-collapse:collapse;table-layout:fixed;margin:14px 0}
            th{background:#eef2f7;font-weight:bold}th,td{border:1px solid #9ca3af;padding:8px 5px;text-align:center;overflow-wrap:anywhere}
            thead{display:table-header-group}tr{break-inside:avoid}
            .instructions{white-space:pre-wrap;line-height:1.5;overflow-wrap:anywhere}
            footer{margin-top:30px;display:flex;justify-content:space-between;break-inside:avoid;font-size:11px}
            </style></head><body><header>$image<h1>${esc(schoolName)}</h1><h2>${esc(ExamPolicy.typeLabel(sheet.text("assessmentType")))} Examination — Datesheet</h2></header>
            <div class='scope'><b>Class:</b> ${esc(sheet.text("className"))} &nbsp; <b>Section:</b> ${esc(sheet.text("section"))}<br><b>Academic Session:</b> ${esc(sheet.text("academicSession"))}</div>
            <table><colgroup><col style='width:6%'><col style='width:18%'><col style='width:17%'><col style='width:30%'><col style='width:29%'></colgroup><thead><tr><th>No.</th><th>Date</th><th>Day</th><th>Subject</th><th>Time</th></tr></thead><tbody>$table</tbody></table>
            <div class='instructions'>${esc(sheet.text("description"))}</div><footer><span>Exam Controller: ______________</span><span>Principal: ______________</span></footer></body></html>"""
    }
    fun print(context: Context, schoolName: String, sheet: DiaryItem,schoolId:String?=null) {
        val logo = cachedSchoolLogo(context,schoolId).ifBlank { runCatching {
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_campus_navy)
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        }.getOrDefault("") }
        val web = WebView(context)
        web.webViewClient = object : WebViewClient() {
            private var opened = false
            override fun onPageFinished(view: WebView, url: String?) {
                if (opened) return
                opened = true
                (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(
                    "EduNexa-Term-Datesheet", view.createPrintDocumentAdapter("Complete Term Datesheet"),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asPortrait())
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
                )
            }
        }
        web.loadDataWithBaseURL(null, html(schoolName, sheet, logo), "text/html", "UTF-8", null)
    }
}

