package pk.edunexa.app.ui
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
class FeePrintDesignTest {
    @Test fun receiptShowsOnlySelectedInstallmentWithEscapedBrandAndRegistration(){val bill=DiaryItem("b",mapOf("studentName" to "<script>Ali</script>","admissionNo" to "008","netPayable" to 2800,"paidAmount" to 1000));val payment=mapOf("receiptNo" to "REC-1","amount" to 300,"paymentDate" to "2026-10-20","paymentMethod" to "Cash","receivedBy" to "Admin");val html=FeePrinter.html("School & Campus",bill,payment,"BASE64");assertTrue(html.contains("Registration: 008"));assertTrue(html.contains("&lt;script&gt;Ali"));assertFalse(html.contains("<script>"));assertTrue(html.contains("Rs. 300"));assertFalse(html.contains("Rs. 1,000"));assertTrue(html.contains("school-logo"));assertTrue(html.contains("A4 portrait"))}
}
