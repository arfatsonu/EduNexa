package pk.edunexa.app.operations
import org.junit.Assert.*
import org.junit.Test
class OwnerPolicyTest {
    @Test fun schoolCardTextHandlesPopulatedAndMissingFieldsWithoutRecursion() {
        val settings: Map<String, Any?> = mapOf("schoolName" to "Al-Noor Science Academy", "campusName" to "Main Campus")
        assertEquals("Al-Noor Science Academy", OwnerPolicy.displayText(settings["schoolName"]))
        assertEquals("school-001 • Main Campus", "school-001 • ${OwnerPolicy.displayText(settings["campusName"])}")
        assertEquals("", OwnerPolicy.displayText(settings["missing"]))
        assertEquals("", OwnerPolicy.displayText(""))
        assertEquals("2026", OwnerPolicy.displayText(2026))
        assertEquals("Unnamed session", OwnerPolicy.displayText(null).ifBlank { "Unnamed session" })
    }
    private val admin=mapOf("role" to "admin","active" to true,"schoolId" to "a")
    @Test fun adminOrForgedProfileFieldDoesNotGrantOwner() {assertFalse(OwnerPolicy.allowed("u","u",emptyMap(),admin+("platformOwner" to true)));assertFalse(OwnerPolicy.allowed("u","u",mapOf("platformOwner" to "true"),admin))}
    @Test fun ownerRequiresClaimActiveAdminAndActualUid() {val claim=mapOf("platformOwner" to true);assertTrue(OwnerPolicy.allowed("u","u",claim,admin));assertFalse(OwnerPolicy.allowed("other","u",claim,admin));assertFalse(OwnerPolicy.allowed("u","u",claim,admin+("active" to false)));assertFalse(OwnerPolicy.allowed("u","u",claim,admin+("role" to "student")));assertFalse(OwnerPolicy.allowed("u","u",claim,admin+("portalDisabled" to true)))}
    @Test fun cannotSuspendOwnSchoolOrUsePathInjection() {assertTrue(runCatching{OwnerPolicy.validateChange("a","a",false)}.isFailure);assertTrue(runCatching{OwnerPolicy.validateChange("a","../b",false)}.isFailure);OwnerPolicy.validateChange("a","b",false);OwnerPolicy.validateChange("a","a",true)}
    @Test fun standaloneOwnerRequiresTrustedClaimWithoutSchoolLink() {
        val owner=mapOf("role" to "owner", "active" to true)
        assertTrue(OwnerPolicy.allowed("u","u",mapOf("platformOwner" to true),owner))
        assertFalse(OwnerPolicy.allowed("u","u",emptyMap(),owner))
    }
}
