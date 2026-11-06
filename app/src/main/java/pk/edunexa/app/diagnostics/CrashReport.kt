package pk.edunexa.app.diagnostics

/** Stack locations only: exception messages can contain account or school data. */
object CrashReport {
    fun format(error: Throwable, version: String, sdk: Int): String = buildString {
        appendLine("EduNexa $version • Android API $sdk")
        var cause: Throwable? = error
        val seen = mutableSetOf<Throwable>()
        repeat(5) {
            val current = cause ?: return@repeat
            if (!seen.add(current)) return@repeat
            appendLine(current.javaClass.name)
            current.stackTrace.take(45).forEach { appendLine("  at $it") }
            cause = current.cause
        }
    }.take(24_000)
}
