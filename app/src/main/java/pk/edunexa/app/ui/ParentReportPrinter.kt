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
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.reports.ParentReport
import java.io.ByteArrayOutputStream

object ParentReportPrinter {
    private fun esc(value: String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    internal fun html(schoolName: String, report: ParentReport, logo: String=""): String {
        val sections=report.sections.joinToString("") { section ->
            val metrics=section.metrics.joinToString("") { (label,value) -> "<span><b>${esc(label)}</b>: ${esc(value)}</span>" }
            val headers=section.headers.joinToString("") { "<th>${esc(it)}</th>" }
            val rows=section.rows.joinToString("") { row -> "<tr>"+row.joinToString("") { "<td>${esc(it)}</td>" }+"</tr>" }.ifBlank { "<tr><td colspan='${section.headers.size}'>No saved records in this selection.</td></tr>" }
            "<section><h2>${esc(section.title)}</h2><div class='metrics'>$metrics</div><table><thead><tr>$headers</tr></thead><tbody>$rows</tbody></table><p class='note'>${esc(section.note)}</p></section>"
        }
        val image=if(logo.isBlank()) "" else "<img class='logo' src='data:image/png;base64,${esc(logo)}'>"
        return """<!doctype html><html><head><meta charset='utf-8'><style>@page{size:A4 portrait;margin:12mm}body{font-family:Arial,sans-serif;font-size:10px;color:#101827;margin:0}header{text-align:center;border-bottom:2px solid #b88624;padding-bottom:12px}.logo{width:55px;height:55px;object-fit:contain}h1{font-size:19px;margin:6px 0}h2{font-size:14px;color:#92540c;margin:18px 0 8px;break-after:avoid}h3{font-size:15px;margin:8px 0}.student{text-align:center;padding:12px;background:#f1f5f9}.metrics{line-height:1.8;margin:7px 0}.metrics span{display:inline-block;margin-right:14px}table{border-collapse:collapse;width:100%;table-layout:fixed}thead{display:table-header-group}th,td{border:1px solid #cbd5e1;padding:6px;text-align:left;overflow-wrap:anywhere;vertical-align:top}th{background:#f1f5f9}tr{break-inside:avoid}.note{font-size:9px;color:#64748b;line-height:1.5}.signatures{display:flex;justify-content:space-between;margin-top:35px;break-inside:avoid}</style></head><body><header>$image<h1>${esc(schoolName)}</h1><h3>Complete Student Report</h3>${esc(report.scope)}<br>Generated ${FeePolicy.today()}</header><div class='student'><b>${esc(report.studentName)}</b><p>${esc(report.details)}</p></div>$sections<div class='signatures'><span>Class Incharge: __________________</span><span>Parent / Guardian: __________________</span></div></body></html>"""
    }
    fun print(context: Context, schoolName: String, report: ParentReport,schoolId:String?=null) {
        val logo=cachedSchoolLogo(context,schoolId).ifBlank { runCatching { val bitmap=BitmapFactory.decodeResource(context.resources,R.drawable.ic_campus_navy); val bytes=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP) }.getOrDefault("") }
        val web=WebView(context)
        web.webViewClient=object: WebViewClient() { private var opened=false
            override fun onPageFinished(view: WebView,url: String?) { if(opened)return;opened=true
                (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("EduNexa-Student-Report",view.createPrintDocumentAdapter("Complete Student Report"),PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asPortrait()).build())
            }
        }
        web.loadDataWithBaseURL(null,html(schoolName,report,logo),"text/html","UTF-8",null)
    }
}

