package pk.edunexa.app.auth

import java.util.Locale

object StudentLoginIdentity {
    fun shortName(value: String): String {
        val short = value.trim().uppercase(Locale.US)
        require(short.matches(Regex("[A-Z][A-Z0-9]{1,11}"))) {
            "Set School Short Form in School Settings (2–12 letters/digits, e.g. ANS or ANPS)."
        }
        return short
    }
    fun email(fullName: String, registration: String, schoolShortName: String, role: String = "student"): String {
        require(role in setOf("student", "teacher"))
        val school = shortName(schoolShortName).lowercase(Locale.US)
        val first = fullName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            .lowercase(Locale.US).replace(Regex("[^a-z]"), "")
        require(first.isNotBlank()) { "Enter the first name in English for the login email." }
        val number = registration.filter { it in '0'..'9' }
        require(registration.any { it in '0'..'9' }) { "Registration / employee number is required for the login email." }
        require(first.length + number.length + (if(role == "teacher") 2 else 0) <= 64) { "First name or registration number is too long." }
        return first + (if(role == "teacher") ".t" else "") + number + "@$school.edu"
    }
}
