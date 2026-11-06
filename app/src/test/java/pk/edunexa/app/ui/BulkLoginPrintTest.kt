package pk.edunexa.app.ui
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
class BulkLoginPrintTest {
    @Test fun slipsPreserveLeadingZerosEscapeNamesAndNeverPrintProfilePasswords() {
        val r=DiaryItem("p",mapOf("fullName" to "<script>Ali</script>","admissionNo" to "008","portalEmail" to "ali008@ans.edu","portalPassword" to "NEVER_PRINT"))
        val html=BulkCardPrinter.html("ANS & School","school-001",listOf(r),false)
        assertTrue(html.contains("ali008@ans.edu"));assertTrue(html.contains("Registration: 008"));assertTrue(html.contains("&lt;script&gt;Ali"));assertFalse(html.contains("<script>"));assertFalse(html.contains("NEVER_PRINT"));assertTrue(html.contains("break-inside:avoid"))
    }
    @Test fun emptyOrOversizedPrintBatchIsRejected() {
        val r=DiaryItem("p",emptyMap());assertTrue(runCatching{BulkCardPrinter.html("School","school",emptyList(),false)}.isFailure);assertTrue(runCatching{BulkCardPrinter.html("School","school",List(101){r},false)}.isFailure)
    }
}
