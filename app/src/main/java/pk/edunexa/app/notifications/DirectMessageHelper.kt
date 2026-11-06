package pk.edunexa.app.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object DirectMessageHelper {
    fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("03") && digits.length >= 11 -> "92" + digits.substring(1)
            digits.startsWith("923") -> digits
            digits.startsWith("00923") -> digits.substring(2)
            else -> digits
        }
    }

    fun sendWhatsApp(context: Context, rawPhone: String, message: String): Boolean {
        val phone = normalizePhone(rawPhone)
        if (phone.isBlank()) return false
        val url = "https://api.whatsapp.com/send?phone=$phone&text=${URLEncoder.encode(message, "UTF-8")}"
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun sendSms(context: Context, rawPhone: String, message: String): Boolean {
        val phone = rawPhone.trim()
        if (phone.isBlank()) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun attendanceText(schoolName: String, studentName: String, rollNo: String, date: String, status: String): String {
        val statusText = when (status) {
            "Absent" -> "Ghair Hazir (Absent)"
            "Late" -> "Late (Taaqeer)"
            "Leave" -> "Rukhsat (On Leave)"
            else -> "Hazir (Present)"
        }
        return "Mohtaram Walidain,\n$schoolName ki taraf se ittila di jati hai ke aapka bacha/bachi $studentName (Roll No: $rollNo) aaj tareekh $date ko school se $statusText hai.\n\nShukriya,\n$schoolName"
    }

    fun feeReceiptText(schoolName: String, studentName: String, rollNo: String, amount: String, date: String, voucherNo: String, balance: String = ""): String {
        val balanceLine = if (balance.isNotBlank() && balance != "0") "\nBaqaya Wajib-ul-Ada: Rs. $balance" else ""
        return "Mohtaram Walidain,\n$schoolName ki taraf se $studentName (Roll No: $rollNo) ki fee Rs. $amount ba-tareekh $date ba-zariya Voucher #$voucherNo kamyabi se wasool ho chuki hai.$balanceLine\n\nShukriya,\n$schoolName Accounts Office"
    }

    fun testMarksText(schoolName: String, studentName: String, rollNo: String, subject: String, testTitle: String, obtained: String, total: String, percentage: String, status: String): String {
        return "Mohtaram Walidain,\n$schoolName: $studentName (Roll No: $rollNo) ka $subject ($testTitle) ka result:\nObtained Marks: $obtained / $total ($percentage%)\nStatus: $status\n\nShukriya,\n$schoolName Examination Department"
    }

    fun broadcastText(schoolName: String, title: String, message: String): String {
        return "📢 *$schoolName - Announcement / Ittila*\n\n*$title*\n\n$message\n\n- $schoolName"
    }

    fun shareWhatsAppBroadcast(context: Context, schoolName: String, title: String, message: String): Boolean {
        val text = broadcastText(schoolName, title, message)
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }, "Share Announcement via WhatsApp")
            runCatching { context.startActivity(chooser); true }.getOrDefault(false)
        }
    }

    fun shareSmsBroadcast(context: Context, schoolName: String, title: String, message: String): Boolean {
        val text = broadcastText(schoolName, title, message)
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val chooser = Intent.createChooser(intent, "Share Announcement")
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}
