package pk.edunexa.app.reports

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.ui.ParentReportPrinter

class ParentReportPrintTest {
    @Test fun reportPrintEscapesUserContentAndContainsAllSections() {
        val sections=listOf("Monthly Attendance","Monthly Homework","Weekly Test").map { ParentReportSection(it,emptyList(),listOf("Name"),listOf(listOf("<script>unsafe</script>")),"Saved records") }
        val html=ParentReportPrinter.html("School & College",ParentReport("<Student>","Details","October 2026",sections))
        assertTrue(html.contains("School &amp; College"));assertTrue(html.contains("&lt;Student&gt;"));assertFalse(html.contains("<script>"));assertTrue(html.contains("A4 portrait"))
        sections.forEach { assertTrue(html.contains(it.title)) }
    }
}
