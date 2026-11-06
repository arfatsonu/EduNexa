package pk.edunexa.app.ui
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.students.StudentPortalData

class StudentReportPrintTest {
    @Test fun escapedReportKeepsAttendanceMonthAndPublishedResults() {
        val student = DiaryItem("own",mapOf("fullName" to "<script>Student</script>","admissionNo" to "A-1","academicSessionId" to "now"))
        val result = DiaryItem("r",mapOf("studentId" to "own","academicSessionId" to "now","testTitle" to "Math & Science","status" to "Absent","publishedAt" to "2026-10-10"))
        val data = StudentPortalData(student,"School & College",emptyList(),emptyList(),emptyList(),emptyList(),listOf(result),emptyList(),emptyList())
        val html = StudentReportPrinter.html(data,"2026-10")
        assertTrue(html.contains("size:A4 portrait"))
        assertTrue(html.contains("&lt;script&gt;Student"))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("Math &amp; Science"))
        assertTrue(html.contains("Absent"))
        assertTrue(html.contains("2026-10"))
    }
}
