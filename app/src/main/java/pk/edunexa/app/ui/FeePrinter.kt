package pk.edunexa.app.ui
import android.content.Context
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.fees.FeePolicy
object FeePrinter {
    internal fun html(schoolName:String,record:DiaryItem,payment:Map<String,Any?>?,logo:String=""):String {
        fun esc(s:String)=PrintDesign.escape(s)
        fun money(n:Double)="Rs. "+java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(n)
        val receipt=payment!=null;val title=if(receipt)"Payment Receipt" else "Fee Bill / Voucher"
        val number=if(receipt)payment?.get("receiptNo")?.toString().orEmpty() else record.text("voucherNo")
        val detail=if(payment!=null)listOf("Payment Date" to payment["paymentDate"].toString(),"Amount Received" to money(FeePolicy.amount(payment,"amount")),"Method" to payment["paymentMethod"].toString(),"Received By" to payment["receivedBy"].toString(),"Reference / Notes" to payment["notes"]?.toString().orEmpty())else listOf("Base Amount" to money(FeePolicy.amount(record.raw,"baseAmount")),"Discount" to money(FeePolicy.amount(record.raw,"discountAmount")),"Fine" to money(FeePolicy.amount(record.raw,"fineAmount")),"Net Payable" to money(FeePolicy.amount(record.raw,"netPayable")),"Total Paid" to money(FeePolicy.paid(record.raw)),"Outstanding" to money(FeePolicy.balance(record.raw)),"Due Date" to record.text("dueDate"))
        val rows=detail.joinToString(""){(label,value)->"<tr><th>${esc(label)}</th><td>${esc(value)}</td></tr>"}
        return """<!doctype html><html><head><meta charset='utf-8'><style>@page{size:A4 portrait;margin:16mm}${PrintDesign.css}body{font-size:12px}table{width:100%;border-collapse:collapse;margin:18px 0}th,td{border:1px solid #cbd5e1;padding:10px;text-align:left}th{width:42%;background:#f7f9fc}.identity{padding:12px;background:#f7f9fc;border-radius:8px}footer{margin-top:32px;font-size:11px}</style></head><body><header class='document-head'>${PrintDesign.logo(logo)}<h1>${esc(schoolName)}</h1><h2>$title</h2><b>${esc(number)}</b></header><div class='identity'><b>${esc(record.text("studentName"))}</b><p>Father: ${esc(record.text("fatherName"))}<br>Class: ${esc(record.text("className"))} • Section: ${esc(record.text("section"))}<br>Registration: ${esc(record.text("admissionNo"))} • Roll: ${esc(record.text("rollNo"))}<br>Charge: ${esc(record.text("feeType"))} • Month: ${esc(record.text("month"))}</p></div><table>$rows</table><footer>Authorized Signature: ____________________<p>Saved school record • EduNexa</p></footer></body></html>"""
    }
    fun print(context:Context,schoolName:String,record:DiaryItem,payment:Map<String,Any?>?,schoolId:String?=null) {
        printOperationsHtml(context,if(payment==null)"EduNexa-Fee-Bill" else "EduNexa-Payment-Receipt",html(schoolName,record,payment,cachedSchoolLogo(context,schoolId)))
    }
}
