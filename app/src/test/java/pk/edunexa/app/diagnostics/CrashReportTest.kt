package pk.edunexa.app.diagnostics

import org.junit.Assert.*
import org.junit.Test

class CrashReportTest {
    @Test fun includesCausesAndLocationsWithoutPrivateMessages() {
        val error = IllegalStateException("private password and email", IllegalArgumentException("school data"))
        val report = CrashReport.format(error, "0.3.4", 36)
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertTrue(report.contains("java.lang.IllegalArgumentException"))
        assertTrue(report.contains("CrashReportTest"))
        assertFalse(report.contains("private password"))
        assertFalse(report.contains("school data"))
        assertTrue(report.length <= 24_000)
    }
}
