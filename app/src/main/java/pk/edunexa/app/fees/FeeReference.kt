package pk.edunexa.app.fees

/** Display only. Stored references remain intact for reconciliation and history. */
object FeeReference {
    fun date(value: String): String = runCatching {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { isLenient = false }
        val date = parser.parse(value) ?: return value
        require(parser.format(date) == value)
        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(date)
    }.getOrDefault(value)

    fun display(value: String): String {
        val id = value.trim()
        if (id.isBlank()) return "—"
        if (id.startsWith("VCH-N-") || id.startsWith("REC-N-") || id.startsWith("REC-") || id.startsWith("VCH-")) {
            val prefix = id.take(3).uppercase(java.util.Locale.US)
            val digitsOnly = id.filter { it.isLetterOrDigit() }.drop(3)
            val shortCode = if (digitsOnly.length > 6) digitsOnly.take(6).uppercase(java.util.Locale.US) else digitsOnly.uppercase(java.util.Locale.US)
            val year = java.text.SimpleDateFormat("yy", java.util.Locale.US).format(java.util.Date())
            return "$prefix-$year-$shortCode"
        }
        return id
    }
}
