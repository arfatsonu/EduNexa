package pk.edunexa.app.teachers

internal object StudentRosterUpdatePolicy {
    private val identity = setOf("admissionNo", "rollNo", "classId", "sectionId", "academicSession", "academicSessionId")
    private val fields = identity + setOf("id", "fullName", "className", "section", "status", "gender", "guardianPhone")
    fun canUpdateExisting(previous: Map<String, Any?>, next: Map<String, Any?>) =
        previous["isDeleted"] != true && previous["academicSessionId"]?.toString()?.isNotBlank() == true &&
            !(previous["status"] == "Inactive" && next["status"] != "Inactive") &&
            identity.all { previous[it] == next[it] }
    fun update(roster: List<Map<String, Any?>>, student: Map<String, Any?>, included: Boolean): List<Map<String, Any?>> {
        val projected = student.filterKeys { it in fields }
        val present = roster.any { it["id"] == student["id"] }
        return roster.mapNotNull { if (it["id"] != student["id"]) it else if (included) projected else null } +
            if (included && !present) listOf(projected) else emptyList()
    }
}
