package pk.edunexa.app.settings
import org.junit.Assert.*
import org.junit.Test
class SchoolSettingsPolicyTest {
    private val profile = mapOf<String, Any?>("schoolName" to "School", "campusName" to "City")
    @Test fun shortFormIsSavedUppercase() { assertEquals("ANPS", SchoolSettingsPolicy.validateProfile(profile + ("schoolShortName" to " anps "))["schoolShortName"]) }
    @Test(expected=IllegalArgumentException::class) fun invalidShortFormRejected() { SchoolSettingsPolicy.validateProfile(profile + ("schoolShortName" to "ANS.edu")) }
    @Test fun profileTrimAndContactValidation() { assertEquals("School", SchoolSettingsPolicy.validateProfile(profile + ("schoolName" to " School "))["schoolName"]) }
    @Test(expected=IllegalArgumentException::class) fun emptySchoolNameRejected() { SchoolSettingsPolicy.validateProfile(profile + ("schoolName" to " ")) }
    @Test(expected=IllegalArgumentException::class) fun invalidEmailRejected() { SchoolSettingsPolicy.validateProfile(profile + ("email" to "user@")) }
    @Test(expected=IllegalArgumentException::class) fun invalidPhoneRejected() { SchoolSettingsPolicy.validateProfile(profile + ("phone" to "abc123")) }
    @Test fun mergePreservesUnknownAndOtherSectionSettings() {
        val original = mapOf<String, Any?>("schoolName" to "Old", "lateCountsAsPresent" to false, "customLogo" to "saved")
        val current = original + ("attendanceMinimumPercent" to 90.0)
        val merged = SchoolSettingsPolicy.mergeSection(current, original, mapOf("schoolName" to "New"))
        assertEquals(false, merged["lateCountsAsPresent"]); assertEquals("saved",merged["customLogo"]); assertEquals(90.0,merged["attendanceMinimumPercent"])
    }
    @Test(expected=IllegalStateException::class) fun concurrentSameSectionChangeRejected() { SchoolSettingsPolicy.mergeSection(mapOf("schoolName" to "Other"),mapOf("schoolName" to "Old"),mapOf("schoolName" to "New")) }
    @Test fun sameRequestRetryIsIdempotent() { assertEquals("New",SchoolSettingsPolicy.mergeSection(mapOf("schoolName" to "New"),mapOf("schoolName" to "Old"),mapOf("schoolName" to "New"))["schoolName"]) }
    @Test(expected=IllegalArgumentException::class) fun invalidThresholdRejected() { SchoolSettingsPolicy.validateAttendance(mapOf("attendanceMinimumPercent" to Double.NaN, "lateCountsAsPresent" to true, "excludeLeaveFromAttendance" to true)) }
    @Test fun attendancePolicyChangesNumeratorAndDenominator() {
        assertEquals(75.0,AttendancePolicy.rate(2,1,1,2,true,true)!!,0.0)
        assertEquals(50.0,AttendancePolicy.rate(2,1,1,2,false,true)!!,0.0)
        assertEquals(50.0,AttendancePolicy.rate(2,1,1,2,true,false)!!,0.0)
        assertNull(AttendancePolicy.rate(0,0,0,2,true,true))
        assertNull(AttendancePolicy.rate(0,0,0,0,true,false))
    }
}
