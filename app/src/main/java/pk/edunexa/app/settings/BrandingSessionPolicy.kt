package pk.edunexa.app.settings

/** Only an authenticated school workspace may load its branding or change its launcher. */
object BrandingSessionPolicy {
    fun schoolId(role: String?, platformOwner: Boolean, schoolId: String?): String? =
        schoolId?.takeIf { !platformOwner && role in setOf("admin", "teacher", "student") && it.isNotBlank() }
}
