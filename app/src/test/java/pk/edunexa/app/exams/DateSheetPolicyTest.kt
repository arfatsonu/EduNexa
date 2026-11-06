package pk.edunexa.app.exams

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.auth.PortalRole

class DateSheetPolicyTest {
    private fun row(subject: String, date: String = "2026-10-20", start: String = "09:00", end: String = "11:00") = mapOf<String, Any?>("subjectId" to subject, "date" to date, "startTime" to start, "endTime" to end)
    @Test fun onlyExplicitlyDeselectedPapersAreExcluded() {
        val rows = listOf(row("math"), row("english", date = "") + ("included" to false))
        val selected = DateSheetPolicy.selectedEntries(rows)
        assertEquals(1, selected.size)
        DateSheetPolicy.validate("1st Term", selected)
        assertFalse(selected[0].containsKey("included"))
    }
    @Test fun selectedBlankPaperGivesActionableSubjectMessage() {
        val result = runCatching { DateSheetPolicy.validate("1st Term", DateSheetPolicy.selectedEntries(listOf(row("math", date = "") + mapOf("subject" to "Mathematics", "included" to true)))) }
        assertEquals("Mathematics: select a paper date.", result.exceptionOrNull()?.message)
    }
    @Test(expected = IllegalArgumentException::class) fun noSelectedPapersCannotBeSaved() {
        DateSheetPolicy.validate("1st Term", DateSheetPolicy.selectedEntries(listOf(row("math") + ("included" to false))))
    }
    @Test fun adjacentPapersAreAllowed() { DateSheetPolicy.validate("1st Term", listOf(row("math"), row("english", start = "11:00", end = "13:00"))) }
    @Test(expected = IllegalArgumentException::class) fun overlappingPapersRejected() { DateSheetPolicy.validate("1st Term", listOf(row("math"), row("english", start = "10:00", end = "12:00"))) }
    @Test(expected = IllegalArgumentException::class) fun duplicateSubjectRejected() { DateSheetPolicy.validate("1st Term", listOf(row("math"), row("math", date = "2026-10-21"))) }
    @Test(expected = IllegalArgumentException::class) fun missingDateRejected() { DateSheetPolicy.validate("2nd Term", listOf(row("math", date = ""))) }
    @Test(expected = IllegalArgumentException::class) fun invalidDateRejected() { DateSheetPolicy.validate("2nd Term", listOf(row("math", date = "2026-02-30"))) }
    @Test(expected = IllegalArgumentException::class) fun backwardsTimeRejected() { DateSheetPolicy.validate("Final Term", listOf(row("math", start = "11:00", end = "09:00"))) }
    @Test(expected = IllegalArgumentException::class) fun invalidTimeRejected() { DateSheetPolicy.validate("Final Term", listOf(row("math", start = "25:00"))) }
    @Test(expected = IllegalArgumentException::class) fun preboardCombinedDatesheetNotEnabled() { DateSheetPolicy.validate("Board Test Session", listOf(row("math"))) }
    @Test(expected = IllegalStateException::class) fun teacherCannotCreateDatesheet() { DateSheetPolicy.requireAdmin(PortalRole.TEACHER) }
    @Test fun adminCanCreateDatesheetButCannotCreateTests() { DateSheetPolicy.requireAdmin(PortalRole.ADMIN); assertTrue(runCatching { ExamPolicy.requireTeacher(PortalRole.ADMIN) }.isFailure) }
    @Test fun stableDatesheetIdentityIncludesSectionAndTerm() { assertEquals(DateSheetPolicy.id("s", "c", "boys", "1st Term"), DateSheetPolicy.id("s", "c", "boys", "1st Term")); assertNotEquals(DateSheetPolicy.id("s", "c", "boys", "1st Term"), DateSheetPolicy.id("s", "c", "girls", "1st Term")); assertNotEquals(DateSheetPolicy.id("s", "c", "boys", "1st Term"), DateSheetPolicy.id("s", "c", "boys", "2nd Term")) }
}
