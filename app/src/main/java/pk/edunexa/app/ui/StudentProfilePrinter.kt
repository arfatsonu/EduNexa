package pk.edunexa.app.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import pk.edunexa.app.students.StudentDirectoryItem
import java.text.NumberFormat
import java.util.Locale

object StudentProfilePrinter {
    fun print(context: Context, student: StudentDirectoryItem, schoolLabel: String,schoolId:String?=null) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val webView = WebView(context);webView.settings.javaScriptEnabled=false

        fun esc(value: String): String =
            value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")

        fun money(value: Double): String {
            val nf = NumberFormat.getNumberInstance(Locale.US)
            nf.maximumFractionDigits = 0
            return "Rs. " + nf.format(value)
        }

        fun row(label: String, value: String): String {
            if (value.isBlank()) return ""
            return "<tr><td class='label'>" + esc(label) + "</td><td>" + esc(value) + "</td></tr>"
        }

        val photo = student.profilePhoto
            ?.takeIf { it.startsWith("data:image") }
            ?.let { "<img class='photo' src='" + it + "' />" }
            .orEmpty()

        val portalRows = if (looksCorrupt(student.portalEmail)) {
            row("Login Status", "Credential repair pending") +
                row("Suggested Email", suggestedEmail(student.admissionNo))
        } else {
            row("Portal Email", student.portalEmail)
        }

        val remarks = if (student.remarks.isNotBlank()) {
            "<h2>Remarks</h2><p>" + esc(student.remarks) + "</p>"
        } else ""

        val html = """
            <html>
            <head>
              <meta charset="utf-8"/>
              <style>
                @page{size:A4 portrait;margin:14mm}
                .school-logo{width:56px;height:56px;object-fit:contain;display:block;margin:0 auto 10px}
                body{font-family:sans-serif;color:#0A0E17;padding:26px;font-size:12px}
                .top{border-bottom:3px solid #E58A13;padding-bottom:14px;margin-bottom:18px}
                h1{font-size:22px;margin:0}.school{font-size:13px;font-weight:700}
                .muted{color:#64748B}.photo{width:84px;height:84px;object-fit:cover;border-radius:12px;float:right}
                h2{font-size:14px;margin:18px 0 8px;background:#F8FAFC;padding:8px;border:1px solid #E2E8F0}
                table{width:100%;border-collapse:collapse}td{padding:6px;border-bottom:1px solid #EEF2F7;vertical-align:top}
                .label{width:38%;color:#64748B;font-weight:600}.footer{margin-top:24px;color:#94A3B8;font-size:10px}
              </style>
            </head>
            <body>
        """.trimIndent() +
            PrintDesign.logo(cachedSchoolLogo(context,schoolId)) + photo +
            "<div class='top'><div class='school'>" + esc(schoolLabel) + "</div>" +
            "<h1>" + esc(student.fullName) + "</h1>" +
            "<div class='muted'>" + esc(student.admissionNo) + " · " +
            esc(student.className) + "-" + esc(student.section) + " · Roll " + esc(student.rollNo) +
            "</div></div>" +
            "<h2>Student Particulars</h2><table>" +
            row("Admission No.", student.admissionNo) +
            row("Admission Date", student.admissionDate) +
            row("Date of Birth", student.dateOfBirth) +
            row("Gender", student.gender) +
            row("Blood Group", student.bloodGroup) +
            row("B-Form / CNIC", student.bForm) +
            row("Class & Section", student.className + " - " + student.section) +
            row("Roll No.", student.rollNo) +
            "</table><h2>Guardian & Contact</h2><table>" +
            row("Father", student.fatherName) +
            row("Occupation", student.fatherOccupation) +
            row("Father CNIC", student.fatherCnic) +
            row("Guardian Mobile", student.guardianPhone) +
            row("Emergency", student.emergencyPhone) +
            row("Address", student.address) +
            "</table><h2>Fee & Portal</h2><table>" +
            row("Tuition Status", student.feeStatus) +
            row("Monthly Fee", money(student.monthlyFee)) +
            row("Saved Tuition Due", if(student.feeBalanceAvailable && student.feeStatus !in setOf("Loading…", "Unavailable")) money(student.balanceDue) else "—") +
            (if (student.annualFund > 0 || student.annualFundSnapshot.balance != null)
                row("Annual Fund Rate", money(student.annualFund)) + row("Annual Fund Status", student.annualFundSnapshot.status) +
                row("Annual Fund Pending", student.annualFundSnapshot.balance?.let { money(it) } ?: "—") else "") +
            (if (student.otherFunds > 0 || student.otherFundsSnapshot.balance != null)
                row("Other Funds Rate", money(student.otherFunds)) + row("Other Funds Status", student.otherFundsSnapshot.status) +
                row("Other Funds Pending", student.otherFundsSnapshot.balance?.let { money(it) } ?: "—") else "") +
            portalRows +
            "</table>" + remarks +
            "<div class='footer'>Generated from EduNexa native Android app.</div></body></html>"

        webView.webViewClient = object : WebViewClient() {
            private var printed=false
            override fun onPageFinished(view: WebView?, url: String?) {
                if(printed)return;printed=true
                val adapter = webView.createPrintDocumentAdapter("Student-" + student.admissionNo)
                printManager.print(
                    "Student Profile - " + student.fullName,
                    adapter,
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    fun looksCorrupt(email: String): Boolean {
        val value = email.trim().lowercase(Locale.US)
        if (value.isBlank()) return true
        val local = value.substringBefore("@", value)
        return value.length > 150 ||
            local.length > 64 ||
            local.contains("cloudstu") ||
            Regex("(cloud.*){2,}").containsMatchIn(local)
    }

    fun suggestedEmail(admissionNo: String): String {
        val local = admissionNo.lowercase(Locale.US).filter { it.isLetterOrDigit() }
        return "Not created yet"
    }
}



