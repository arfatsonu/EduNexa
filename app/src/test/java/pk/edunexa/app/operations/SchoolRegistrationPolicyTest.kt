package pk.edunexa.app.operations
import org.junit.Assert.*
import org.junit.Test
class SchoolRegistrationPolicyTest {
    private val setup=SchoolRegistration("new-school","Real School","Main Campus","ANS","Principal","principal@example.com")
    @Test fun validatesNormalizesAndKeepsAcademicSetupEmpty(){val result=SchoolRegistrationPolicy.validate(setup.copy(adminEmail="Principal@Example.com"),"SecurePassword123");assertEquals("principal@example.com",result.adminEmail);assertTrue(SchoolRegistrationPolicy.emptyConfig().values.all{it is List<*>&&it.isEmpty()});assertFalse(SchoolRegistrationPolicy.emptyConfig().containsKey("password"))}
    @Test fun rejectsPathsBadEmailAndWeakPassword(){assertTrue(runCatching{SchoolRegistrationPolicy.validate(setup.copy(schoolId="../other"),"SecurePassword123")}.isFailure);assertTrue(runCatching{SchoolRegistrationPolicy.validate(setup.copy(adminEmail="invalid"),"SecurePassword123")}.isFailure);assertTrue(runCatching{SchoolRegistrationPolicy.validate(setup,"12345678")}.isFailure)}
}
