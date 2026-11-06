package pk.edunexa.app.exams

import pk.edunexa.app.academic.AcademicPolicy
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.diary.DiaryPolicy

object DateSheetPolicy {
    val terms = listOf("1st Term", "2nd Term", "Final Term")
    fun requireAdmin(role: PortalRole) { check(role == PortalRole.ADMIN) { "Only Admin can create a term datesheet." } }
    fun selectedEntries(rows: List<Map<String, Any?>>): List<Map<String, Any?>> =
        rows.filter { it["included"] != false }.map { it - "included" }

    fun validate(term: String, entries: List<Map<String, Any?>>) {
        require(term in terms) { "Select a term." }
        require(entries.isNotEmpty() && entries.size <= 40) { "Add subjects to the datesheet." }
        require(entries.map { it["subjectId"] }.distinct().size == entries.size && entries.all { !it["subjectId"].toString().isBlank() && it["subjectId"] != null }) { "A subject is missing or duplicated." }
        entries.forEach { row ->
            val subject = row["subject"]?.toString()?.takeIf { it.isNotBlank() } ?: "Each subject"
            val date = row["date"]?.toString().orEmpty()
            require(date.isNotBlank()) { "$subject: select a paper date." }
            AcademicPolicy.validateDates(date, date)
            val start = row["startTime"]?.toString().orEmpty(); val end = row["endTime"]?.toString().orEmpty()
            require(start.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) && end.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) && start < end) { "$subject: select start and end times; end must be after start." }
        }
        entries.groupBy { it["date"] }.values.forEach { rows ->
            val sorted = rows.sortedBy { it["startTime"].toString() }
            require(sorted.zipWithNext().all { (a, b) -> a["endTime"].toString() <= b["startTime"].toString() }) { "Paper timings overlap on the same date." }
        }
    }
    fun id(sessionId: String, classId: String, sectionId: String, term: String) = "datesheet-" + DiaryPolicy.key(sessionId, classId, sectionId, term)
}

