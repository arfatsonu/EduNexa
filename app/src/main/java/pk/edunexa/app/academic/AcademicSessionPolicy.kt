package pk.edunexa.app.academic

/** IDs are opaque; only complete academic-year labels may share punctuation aliases. */
object AcademicSessionPolicy {
    /** Match Admin projection behavior without inventing a session for an empty school. */
    fun current(config: Map<String, Any?>): Map<String, Any?> {
        val sessions = (config["sessions"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
        }.orEmpty()
        return sessions.firstOrNull { it["isCurrent"] == true }
            ?: sessions.firstOrNull { it["status"]?.toString().equals("Active", true) }
            ?: emptyMap()
    }

    private fun year(value: String): String? = Regex("^(\\d{4})\\s*[-–—/]\\s*(\\d{4})$").matchEntire(value.trim())?.let { it.groupValues[1] + "-" + it.groupValues[2] }
    fun matches(record: String?, aliases: Collection<String>, allowMissing: Boolean = false): Boolean {
        val value = record.orEmpty().trim()
        if (value.isBlank()) return allowMissing
        return aliases.filter { it.isNotBlank() }.any { alias -> value == alias.trim() || (year(value) != null && year(value) == year(alias)) }
    }
    fun enrolled(status: String): Boolean = status.isBlank() || status.equals("Active", true) || status.equals("On Leave", true)
}
