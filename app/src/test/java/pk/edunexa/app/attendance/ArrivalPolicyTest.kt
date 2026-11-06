package pk.edunexa.app.attendance
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ArrivalPolicyTest {
    private val settings=mapOf("autoLateEnabled" to true,"schoolStartTime" to "08:00","lateGraceMinutes" to 10)
    @Test fun beforeAndAtDeadlineArePresentAfterDeadlineIsLate() {
        assertEquals("Present",ArrivalPolicy.fields(settings,Instant.parse("2026-10-20T03:09:59Z"))["status"])
        assertEquals("Present",ArrivalPolicy.fields(settings,Instant.parse("2026-10-20T03:10:00Z"))["status"])
        assertEquals("Late",ArrivalPolicy.fields(settings,Instant.parse("2026-10-20T03:10:01Z"))["status"])
    }
    @Test fun legacySchoolWithoutPolicyRecordsArrivalAndStaysPresent() {
        val row=ArrivalPolicy.fields(emptyMap(),Instant.parse("2026-10-20T05:01:02Z"))
        assertEquals("Present",row["status"]);assertEquals("10:01:02",row["arrivalTime"])
    }
    @Test fun dateAndTimeUsePakistanRegardlessOfDefaultTimezone() {
        val original=java.util.TimeZone.getDefault()
        try {java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Honolulu"));val row=ArrivalPolicy.fields(settings,Instant.parse("2026-10-19T19:00:01Z"));assertEquals("2026-10-20",row["arrivalDate"]);assertEquals("00:00:01",row["arrivalTime"])}finally{java.util.TimeZone.setDefault(original)}
    }
    @Test fun invalidTimeFractionalOrNegativeGraceAndDayWrapAreRejected() {
        listOf(settings+("schoolStartTime" to "8:00"),settings+("schoolStartTime" to "25:00"),settings+("lateGraceMinutes" to -1),settings+("lateGraceMinutes" to 1.5),settings+("schoolStartTime" to "23:55")).forEach {assertTrue(runCatching{ArrivalPolicy.validate(it)}.isFailure)}
    }
}
