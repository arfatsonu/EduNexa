package pk.edunexa.app.operations

import org.junit.Assert.*
import org.junit.Test

class OwnerSchoolPolicyTest {
    private val original = mapOf<String, Any?>("schoolName" to "Example School", "campusName" to "Main", "schoolShortName" to "EX", "address" to "Old street", "brandPrimary" to "#0A0E17", "attendanceMinimumPercent" to 75.0, "custom" to "retained")
    @Test fun onlyChangedProfileFieldsAreWrittenAndOtherSettingsSurvive() {
        val patch = OwnerSchoolPolicy.patch(original, original + ("address" to "New street"))
        assertEquals(mapOf("address" to "New street"), patch)
        val merged = OwnerSchoolPolicy.merge(original, original, patch)
        assertEquals("retained", merged["custom"]); assertEquals(75.0, merged["attendanceMinimumPercent"]); assertEquals("#0A0E17", merged["brandPrimary"])
    }
    @Test fun concurrentUnrelatedChangeIsPreserved() {
        val live = original + ("attendanceMinimumPercent" to 80.0)
        val merged = OwnerSchoolPolicy.merge(live, original, mapOf("address" to "New street"))
        assertEquals(80.0, merged["attendanceMinimumPercent"])
    }
    @Test fun concurrentSameFieldConflictCannotOverwrite() {
        assertTrue(runCatching { OwnerSchoolPolicy.merge(original + ("address" to "Other device"), original, mapOf("address" to "New street")) }.isFailure)
        assertEquals("Same", OwnerSchoolPolicy.merge(original + ("address" to "Same"), original, mapOf("address" to "Same"))["address"])
    }
    @Test fun roleAndAccessFieldsCannotBePatched() {
        for(key in listOf("adminUid", "portalAccessEnabled", "brandPrimary", "role", "attendanceMinimumPercent")) assertTrue(runCatching { OwnerSchoolPolicy.merge(original, original, mapOf(key to "forged")) }.isFailure)
    }
    @Test fun schoolAndLogoValidation() {
        assertTrue(runCatching { OwnerSchoolPolicy.patch(original, original + ("schoolName" to "")) }.isFailure)
        assertTrue(runCatching { OwnerSchoolPolicy.patch(original, original + ("schoolShortName" to "")) }.isFailure)
        assertTrue(runCatching { OwnerSchoolPolicy.patch(original, original + ("schoolLogo" to "https://foreign.example/logo.png")) }.isFailure)
        assertTrue(runCatching { OwnerSchoolPolicy.patch(original, original + ("address" to "x".repeat(1001))) }.isFailure)
        assertEquals("", OwnerSchoolPolicy.patch(original + ("schoolLogo" to "data:image/png;base64,iVBORw0KGgoAAA=="), original)["schoolLogo"])
    }
    @Test fun emailAndPasswordValidation() {
        assertEquals("admin@example.com", OwnerSchoolPolicy.email(" Admin@Example.com "))
        assertTrue(runCatching { OwnerSchoolPolicy.email("bad email") }.isFailure)
        assertTrue(runCatching { OwnerSchoolPolicy.password("SafePass12345", "different") }.isFailure)
        assertTrue(runCatching { OwnerSchoolPolicy.password("short", "short") }.isFailure)
        OwnerSchoolPolicy.password("SafePass12345", "SafePass12345")
    }
    @Test fun accountMustBeAnActiveAdminInTheSelectedSchool() {
        val profile = mapOf("role" to "admin", "schoolId" to "a", "active" to true)
        OwnerSchoolPolicy.admin(profile, "admin", "a", "owner")
        assertTrue(runCatching { OwnerSchoolPolicy.admin(profile, "admin", "b", "owner") }.isFailure)
        assertTrue(runCatching { OwnerSchoolPolicy.admin(profile, "owner", "a", "owner") }.isFailure)
        for(change in listOf(mapOf("role" to "owner"), mapOf("active" to false), mapOf("portalDisabled" to true))) assertTrue(runCatching { OwnerSchoolPolicy.admin(profile + change, "admin", "a", "owner") }.isFailure)
    }
}
