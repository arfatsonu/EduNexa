package pk.edunexa.app.exams
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
class GazettePolicyTest {
    private val test = DiaryItem("t", mapOf("studentIds" to listOf("a"), "totalMarks" to 20, "passingMarks" to 8))
    private fun mark(id: String, score: Double?, status: String = "Present", time: String = "") = DiaryItem(id, mapOf("testId" to "t", "studentId" to "a", "status" to status, "marksObtained" to score, "updatedAt" to time))
    @Test fun unrelatedTestDoesNotInflateTotalsOrPending() {
        val cell = GazettePolicy.cell(test, "b", emptyList())
        assertEquals("N/A", cell.label); assertEquals(0.0, cell.total, 0.0); assertFalse(cell.pending)
    }
    @Test fun invalidOrMissingMarksNeverProducePass() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 21.0, null).forEach { assertTrue(GazettePolicy.cell(test, "a", listOf(mark("m", it))).pending) }
        assertTrue(GazettePolicy.cell(test, "a", listOf(mark("m", 10.0, "Pending"))).pending)
    }
    @Test fun latestCorrectionWinsAndAbsenceRemainsExplicit() {
        assertEquals(12.0, GazettePolicy.cell(test, "a", listOf(mark("old", 3.0, time="2026-10-01"), mark("new", 12.0, time="2026-10-02"))).obtained, 0.0)
        assertEquals("Absent", GazettePolicy.cell(test, "a", listOf(mark("m", null, "Absent"))).label)
        assertEquals(0.0, GazettePolicy.cell(test, "a", listOf(mark("m", null, "Exempt"))).total, 0.0)
    }
}
