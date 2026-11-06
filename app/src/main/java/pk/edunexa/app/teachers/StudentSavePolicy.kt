package pk.edunexa.app.teachers

internal object StudentSavePolicy {
    private val rosterFields = setOf("fullName", "admissionNo", "rollNo", "classId", "sectionId", "className", "section", "status", "gender", "guardianPhone", "academicSession", "academicSessionId")
    fun canSkipDirectory(previous: Map<String, Any?>, next: Map<String, Any?>): Boolean =
        previous["academicSessionId"]?.toString()?.isNotBlank() == true &&
            rosterFields.all { previous[it] == next[it] }
}
