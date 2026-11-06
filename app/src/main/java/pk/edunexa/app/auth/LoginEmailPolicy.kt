package pk.edunexa.app.auth

import java.util.Locale

object LoginEmailPolicy {
    fun choose(custom: String, suggested: String): String {
        val email = custom.trim().ifBlank { suggested }.lowercase(Locale.US)
        require(email.length <= 254 && email.matches(Regex("[a-z0-9][a-z0-9._+%-]{0,63}@[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}"))) { "Enter a valid login email." }
        return email
    }
}
