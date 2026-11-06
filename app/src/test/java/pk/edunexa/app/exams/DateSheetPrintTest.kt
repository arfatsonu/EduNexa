package pk.edunexa.app.exams
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.ui.DateSheetPrinter
class DateSheetPrintTest {
    private fun sheet(rows: List<Map<String, Any?>>) = DiaryItem("saved", mapOf("assessmentType" to "1st Term", "className" to "Class 9", "section" to "Diamond", "academicSession" to "2026–2027", "description" to "<script>unsafe</script>", "datesheetEntries" to rows))
    private fun row(subject: String, date: String) = mapOf<String, Any?>("subject" to subject, "date" to date, "startTime" to "09:00", "endTime" to "11:00")
    @Test fun completeDatesheetIncludesEveryPaperAndA4Settings() {
        val rows = (1..11).map { row("Subject $it", "2026-10-${it + 10}") }
        val html = DateSheetPrinter.html("EduNexa", sheet(rows))
        for(i in 1..11) assertTrue(html.contains(">Subject $i</td>"))
        assertTrue(html.contains("size:A4 portrait"))
        assertTrue(html.contains("Class 9"))
        assertTrue(html.contains("Diamond"))
        assertTrue(html.contains("2026–2027"))
        assertTrue(html.contains("9:00 AM"))
        assertTrue(html.contains("11:00 AM"))
        assertTrue(html.contains("&lt;script&gt;unsafe&lt;/script&gt;"))
        assertFalse(html.contains("<script>"))
    }
    @Test fun savedPapersAreSortedByDateWithoutMutatingSheet() {
        val rows = listOf(row("Later", "2026-10-22"), row("Earlier", "2026-10-20"))
        val saved = sheet(rows)
        val html = DateSheetPrinter.html("School", saved)
        assertTrue(html.indexOf(">Earlier</td>") < html.indexOf(">Later</td>"))
        assertEquals(rows, saved.raw["datesheetEntries"])
    }
    @Test fun longSheetsRetainAllPapersAndRepeatTableHeaders() {
        val rows = (1..40).map { row("Paper $it", "2026-10-20") }
        val html = DateSheetPrinter.html("School", sheet(rows))
        assertEquals(40, Regex("<tr><td>").findAll(html).count())
        assertTrue(html.contains("thead{display:table-header-group}"))
        assertTrue(html.contains("break-inside:avoid"))
    }
}
