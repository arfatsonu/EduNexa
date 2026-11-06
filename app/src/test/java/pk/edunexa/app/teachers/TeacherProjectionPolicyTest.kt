package pk.edunexa.app.teachers

import org.junit.Assert.*
import org.junit.Test

class TeacherProjectionPolicyTest {
    @Test fun timestampsDoNotInvalidateTheRoster() {
        val profile = mapOf<String, Any?>("id" to "t1", "name" to "Teacher", "updatedAt" to "old", "academicDirectoryUpdatedAt" to "old")
        assertEquals(TeacherProjectionPolicy.rosterEntry(profile), TeacherProjectionPolicy.rosterEntry(profile + mapOf("updatedAt" to "new", "academicDirectoryUpdatedAt" to "new")))
        assertNotEquals(TeacherProjectionPolicy.rosterEntry(profile), TeacherProjectionPolicy.rosterEntry(profile + ("name" to "Changed")))
    }
    @Test fun credentialsAndLargeNestedDataStayOutOfRoster() {
        val projected = TeacherProjectionPolicy.rosterEntry(mapOf("id" to "t1", "phone" to "123", "portalPassword" to "secret", "profilePhoto" to "photo", "academicDirectory" to emptyMap<String, Any>(), "subjectAccessKeys" to listOf("scope")))
        assertEquals(mapOf("id" to "t1", "phone" to "123"), projected)
    }
}
