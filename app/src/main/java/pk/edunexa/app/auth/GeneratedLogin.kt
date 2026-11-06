package pk.edunexa.app.auth

import java.util.Locale

data class GeneratedLogin(val email: String, val password: String)

object LoginGenerator {
    private fun digest(role: String, schoolId: String, profileId: String): String {
        require(role in setOf("student", "teacher") && schoolId.isNotBlank() && profileId.isNotBlank())
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest("$schoolId/$role/$profileId".toByteArray(Charsets.UTF_8))
            .take(10).joinToString("") { "%02x".format(Locale.US, it.toInt() and 255) }
    }
    fun password(role: String, schoolId: String, profileId: String, identifier: String, fullName: String): String {
        val digest = digest(role, schoolId, profileId)
        val first = fullName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().replace(Regex("[^A-Za-z]"), "")
            .take(24).ifBlank { if(role == "student") "Student" else "Teacher" }
            .lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        val suffix = identifier.filter { it.isDigit() }.takeLast(12).ifBlank { identifier.filter { it.isLetterOrDigit() }.take(12) }
            .ifBlank { digest.take(6) }
        return first + suffix.padStart(maxOf(suffix.length, 6 - first.length), '0')
    }
    fun generate(role: String, schoolId: String, profileId: String, identifier: String = "", fullName: String = "", schoolShortName: String = "", customEmail: String = ""): GeneratedLogin {
        digest(role, schoolId, profileId)
        // Both roles require the school's short form; never generate a hashed fallback.
        val email = if (customEmail.isBlank()) StudentLoginIdentity.email(fullName, identifier, schoolShortName, role)
            else LoginEmailPolicy.choose(customEmail, "")
        return GeneratedLogin(email, password(role, schoolId, profileId, identifier, fullName))
    }
}
