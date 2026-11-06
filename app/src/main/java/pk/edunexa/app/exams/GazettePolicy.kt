package pk.edunexa.app.exams

import pk.edunexa.app.diary.DiaryItem

/** A test only contributes to a student's gazette when that student belongs to its saved roster. */
object GazettePolicy {
    data class Cell(val label: String, val total: Double = 0.0, val obtained: Double = 0.0, val pending: Boolean = false, val failed: Boolean = false)
    fun latest(marks: List<DiaryItem>): DiaryItem? = marks.maxWithOrNull(compareBy<DiaryItem>({ it.text("updatedAt").ifBlank { it.text("markedAt") } }, { it.id }))
    fun cell(test: DiaryItem, studentId: String, marks: List<DiaryItem>): Cell {
        val roster = (test.raw["studentIds"] as? List<*>)?.filterIsInstance<String>()
        if (roster != null && studentId !in roster) return Cell("N/A")
        val mark = latest(marks.filter { it.text("studentId") == studentId && it.text("testId") == test.id })
        if (roster == null && mark == null) return Cell("N/A")
        val max = (test.raw["totalMarks"] as? Number)?.toDouble() ?: 0.0
        val passing = (test.raw["passingMarks"] as? Number)?.toDouble() ?: 0.0
        val status = mark?.text("status")
        if (status == "Exempt") return Cell("Exempt")
        if (!max.isFinite() || max <= 0 || !passing.isFinite() || passing < 0 || passing > max) return Cell("Pending", pending = true)
        if (status == "Absent") return Cell("Absent", max, failed = true)
        val score = (mark?.raw?.get("marksObtained") as? Number)?.toDouble()
        if (status != "Present" || score == null || !score.isFinite() || score < 0 || score > max) return Cell("Pending", max, pending = true)
        fun num(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)
        return Cell(num(score) + "/" + num(max), max, score, failed = score < passing)
    }
}
