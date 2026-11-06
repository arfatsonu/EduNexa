package pk.edunexa.app.fees

import pk.edunexa.app.diary.DiaryItem

/** Removed or missing profiles retain history, but never contribute to current fees. */
object FeeVisibility {
    fun currentStudentIds(students: List<DiaryItem>): Set<String> = students
        .filter { it.id.isNotBlank() && it.raw["isDeleted"] != true }.map { it.id }.toSet()
    fun isCurrent(record: Map<String, Any?>, studentIds: Set<String>): Boolean =
        record["studentId"]?.toString()?.takeIf { it.isNotBlank() } in studentIds
}
