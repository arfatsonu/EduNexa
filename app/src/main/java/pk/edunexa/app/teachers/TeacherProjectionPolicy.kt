package pk.edunexa.app.teachers

internal object TeacherProjectionPolicy {
    private val excluded = setOf("academicDirectory", "subjectAccessKeys", "portalPassword", "profilePhoto", "updatedAt", "academicDirectoryUpdatedAt")
    fun rosterEntry(profile: Map<String, Any?>) = profile.filterKeys { it !in excluded }
}
