package pk.edunexa.app.teachers

/** Directory removal keeps the profile as a tombstone for historical references. */
object TeacherDeletionPolicy {
    fun remainingAssignments(assignments: List<Map<String, Any?>>, teacherId: String) =
        assignments.filter { it["teacherId"] != teacherId }

    fun availableLoginEmail(proposed: String, profileId: String, reserved: Set<String>): String {
        val normalized = reserved.map { it.lowercase(java.util.Locale.US) }.toSet()
        if (proposed.lowercase(java.util.Locale.US) !in normalized) return proposed
        // Removed accounts stay reserved; use a short numeric suffix rather than a profile UUID.
        require(profileId.isNotBlank()) { "A valid new teacher ID is required." }
        for(number in 2..9999) {
            val alternative = proposed.substringBefore("@") + "." + number + "@" + proposed.substringAfter("@")
            if(alternative.lowercase(java.util.Locale.US) !in normalized) return alternative
        }
        error("This login email is already reserved. Use another Employee ID.")
    }
}
