
package pk.edunexa.app.auth

import java.security.MessageDigest

object CampusLoginIdentity {
    fun email(role: String, schoolId: String, identifier: String): String {
        require(role in setOf("student", "teacher"))
        require(schoolId.isNotBlank() && identifier.isNotBlank())
        val school = MessageDigest.getInstance("SHA-256").digest(schoolId.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(java.util.Locale.US, it.toInt() and 255) }
        val local = identifier.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9.]"), "").take(32)
        require(local.isNotBlank())
        return "$local.$school@$role.a2campus.app"
    }
}
