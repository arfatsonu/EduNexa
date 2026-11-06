package pk.edunexa.app.settings

import org.junit.Assert.*
import org.junit.Test

class BrandingSessionPolicyTest {
    @Test fun checkingAndOwnerNeverUseCachedSchoolBranding() {
        assertNull(BrandingSessionPolicy.schoolId(null, false, "school-001"))
        assertNull(BrandingSessionPolicy.schoolId("owner", true, "school-001"))
        assertNull(BrandingSessionPolicy.schoolId("admin", true, "school-001"))
        assertNull(BrandingSessionPolicy.schoolId("owner", false, "school-001"))
    }
    @Test fun verifiedSchoolRolesKeepOnlyTheirOwnSchool() {
        for (role in listOf("admin", "teacher", "student")) {
            assertEquals("school-002", BrandingSessionPolicy.schoolId(role, false, "school-002"))
            assertNull(BrandingSessionPolicy.schoolId(role, false, ""))
            assertNull(BrandingSessionPolicy.schoolId(role, false, null))
        }
    }
}
