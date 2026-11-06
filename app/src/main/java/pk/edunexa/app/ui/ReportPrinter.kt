package pk.edunexa.app.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.reports.ReportDocument

object ReportPrinter {
    private fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun print(context: Context, schoolName: String, report: ReportDocument,schoolId:String?=null) {
        val summary = report.metrics.joinToString("") { (label, value) -> "<div><b>${esc(label)}</b><br>${esc(value)}</div>" }
        val headers = report.headers.joinToString("") { "<th>${esc(it)}</th>" }
        val rows = report.rows.joinToString("") { row -> "<tr>" + row.joinToString("") { "<td>${esc(it)}</td>" } + "</tr>" }
        val notes = report.notes.joinToString("") { "<p>${esc(it)}</p>" }
        val html = """<html><head><meta charset='utf-8'><style>@page{size:A4 landscape;margin:12mm}body{font-family:sans-serif;color:#101827;font-size:11px}h1,h2{text-align:center}h1{font-size:20px}h2{font-size:16px;color:#92540c}.scope{text-align:center}.summary{display:flex;margin:20px 0}.summary div{flex:1;padding:10px;border:1px solid #cbd5e1}table{border-collapse:collapse;width:100%;table-layout:fixed}th,td{border:1px solid #cbd5e1;padding:7px;overflow-wrap:anywhere}th{background:#eff3f8;text-align:left}thead{display:table-header-group}tr{break-inside:avoid}.notes{font-size:10px;color:#64748b}</style></head><body><h1>${esc(schoolName)}</h1><h2>${esc(report.title)}</h2><p class='scope'>${esc(report.scope)}<br>Generated ${FeePolicy.today()} • ${report.rows.size} records</p><div class='summary'>$summary</div><table><thead><tr>$headers</tr></thead><tbody>$rows</tbody></table><div class='notes'>$notes</div></body></html>"""
        val branded=html.replace("<style>","<style>"+PrintDesign.css).replace("<body><h1>","<body>"+PrintDesign.logo(cachedSchoolLogo(context,schoolId))+"<h1>")
        val web = WebView(context);web.settings.javaScriptEnabled=false
        web.webViewClient = object : WebViewClient() { private var printed=false;override fun onPageFinished(view: WebView, url: String?) {
            if(printed)return;printed=true
            (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("EduNexa-${report.title}", view.createPrintDocumentAdapter(report.title), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape()).build())
        } }
        web.loadDataWithBaseURL(null, branded, "text/html", "UTF-8", null)
    }
}
