package pk.edunexa.app.reports

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.fees.FeePolicy

class ReportPolicyTest {
    private val config = mapOf<String, Any?>("sessions" to listOf(mapOf("id" to "s", "name" to "2026–2027", "isCurrent" to true)), "classes" to listOf(mapOf("id" to "c", "name" to "Class 9")), "sections" to listOf(mapOf("id" to "a", "name" to "Boys", "classId" to "c")))
    private fun row(id: String, fields: Map<String, Any?>) = DiaryItem(id, mapOf("academicSessionId" to "s", "classId" to "c", "className" to "Class 9", "sectionId" to "a", "section" to "Boys") + fields)
    private fun data(rows: List<DiaryItem>, related: List<DiaryItem> = emptyList()) = ReportData(config, rows, related, "School")
    @Test fun allStudentSessionsIncludesLegacyProfilesAndStillRespectsClass() {
        val rows = listOf(row("current", mapOf("fullName" to "Current")), row("legacy", mapOf("fullName" to "Legacy", "academicSessionId" to "")), row("old", mapOf("fullName" to "Old", "academicSessionId" to "old")), row("other", mapOf("classId" to "other")))
        val doc = ReportPolicy.build("Students", data(rows), ReportFilter("__all__", "c"))
        assertEquals(3, doc.rows.size)
        assertTrue(doc.scope.contains("All Sessions"))
        assertEquals("Session not set", doc.rows.first { it.first() == "Legacy" }[5])
        assertEquals("—", doc.rows.first { it.first() == "Legacy" }[6])
    }
    @Test fun sessionAndClassFiltersDoNotLeakOtherRecords() {
        val rows = listOf(row("yes", mapOf("fullName" to "Own")), row("old", mapOf("academicSessionId" to "old")), row("otherClass", mapOf("classId" to "other")), row("unscoped", mapOf("academicSessionId" to "")))
        val doc = ReportPolicy.build("Students", data(rows), ReportFilter("s", "c", "a"))
        assertEquals(1, doc.rows.size); assertEquals("Own", doc.rows.single().first())
    }
    @Test fun legacySessionNameMatchesWithoutTreatingMissingSessionAsCurrent() {
        val row = row("yes", mapOf("academicSessionId" to "2026–2027"))
        assertTrue(ReportPolicy.scoped(row, data(emptyList()), ReportFilter("s")))
        assertFalse(ReportPolicy.scoped(row.copy(raw = row.raw + ("academicSessionId" to "")), data(emptyList()), ReportFilter("s")))
    }
    @Test fun noAttendanceHasNoFabricatedPercentOrAbsences() {
        val doc = ReportPolicy.build("Attendance", data(emptyList()), ReportFilter("s"))
        assertEquals("—", doc.metrics.first { it.first == "Recorded Attendance Rate" }.second); assertEquals("0 / 0", doc.metrics[2].second)
    }
    @Test fun attendanceRateCountsLateAndDeduplicatesUpdatedStudentDay() {
        val fields = mapOf("attendanceDate" to "2026-10-01", "studentId" to "one", "studentName" to "A")
        val rows = listOf(row("old", fields + mapOf("status" to "Absent", "markedAt" to "2026-10-01T08:00")), row("new", fields + mapOf("status" to "Late", "markedAt" to "2026-10-01T09:00")), row("unmarked", fields + mapOf("studentId" to "two", "status" to "Unmarked")))
        val doc = ReportPolicy.build("Attendance", data(rows), ReportFilter("s", month = "October 2026"))
        assertEquals(2, doc.rows.size); assertEquals("1", doc.metrics[0].second); assertEquals("100.0%", doc.metrics.first { it.first == "Recorded Attendance Rate" }.second)
    }
    @Test fun fundsStayWholeSessionAndEarlierPaidTuitionIsNotIncludedAsDebt() {
        fun bill(id: String, type: String, month: String, paid: Double) = row(id, mapOf("feeType" to type, "month" to month, "netPayable" to 100.0, "paidAmount" to paid))
        val rows = listOf(bill("annual", "Annual Fund", "September 2026", 0.0), bill("oldPaid", FeePolicy.TUITION, "September 2026", 100.0), bill("oldDebt", FeePolicy.TUITION, "September 2026", 40.0), bill("now", FeePolicy.TUITION, "October 2026", 0.0), bill("future", FeePolicy.TUITION, "November 2026", 0.0))
        val doc = ReportPolicy.build("Fees", data(rows), ReportFilter("s", month = "October 2026"))
        assertEquals(3, doc.rows.size)
        assertEquals(ReportPolicy.money(200.0), doc.metrics[0].second)
        assertEquals(ReportPolicy.money(100.0), doc.metrics[3].second)
    }
    @Test fun pendingAndAbsentMarksDoNotBecomeZeroInAverage() {
        val test = row("test", mapOf("date" to "2026-10-01", "title" to "Math", "totalMarks" to 20.0, "studentIds" to listOf("one", "two", "three")))
        val marks = listOf(row("m1", mapOf("testId" to "test", "studentId" to "one", "status" to "Present", "marksObtained" to 10.0)), row("m2", mapOf("testId" to "test", "studentId" to "two", "status" to "Absent")), row("m3", mapOf("testId" to "test", "studentId" to "three", "status" to "Present", "marksObtained" to null)))
        val doc = ReportPolicy.build("Exams", data(listOf(test), marks), ReportFilter("s", month = "October 2026"))
        assertEquals("2 / 3", doc.rows.single()[5]); assertEquals("50.0%", doc.rows.single()[6])
    }
    @Test fun monthParsingRejectsImpossibleDates() { assertEquals("", ReportPolicy.dateMonth("2026-02-31")); assertEquals("October 2026", ReportPolicy.dateMonth("2026-10-01")) }
    @Test fun reportsFollowSavedLateAndLeavePolicyAndMinimumThreshold() {
        val fields = mapOf("attendanceDate" to "2026-10-01", "studentId" to "one", "studentName" to "A")
        val rows = listOf(row("p",fields + ("status" to "Present")),row("l",fields + mapOf("status" to "Late", "attendanceDate" to "2026-10-02")),row("leave",fields + mapOf("status" to "Leave", "attendanceDate" to "2026-10-03")))
        val settings = mapOf<String, Any?>("lateCountsAsPresent" to false, "excludeLeaveFromAttendance" to true, "attendanceMinimumPercent" to 75.0)
        val doc = ReportPolicy.build("Attendance",data(rows).copy(settings=settings),ReportFilter("s",month="October 2026"))
        assertEquals("50.0%",doc.metrics.first { it.first == "Recorded Attendance Rate" }.second)
        assertEquals("1",doc.metrics.first { it.first == "Students Below Minimum" }.second)
    }
}


