package pk.edunexa.app.academic
import org.junit.Assert.*
import org.junit.Test
class AcademicBootstrapPolicyTest {
    @Test fun emptySchoolCanSaveItsFirstUpcomingSessionBeforeSelectingCurrent(){assertTrue(AcademicBootstrapPolicy.emptySchoolSetup(0,0,0,mapOf("sessions" to listOf(mapOf("id" to "first","status" to "Upcoming"))),false))}
    @Test fun existingPeopleAssignmentsOrProfileCreationCannotBypassActiveSession(){assertFalse(AcademicBootstrapPolicy.emptySchoolSetup(1,1,0,emptyMap(),false));assertFalse(AcademicBootstrapPolicy.emptySchoolSetup(0,0,1,emptyMap(),false));assertFalse(AcademicBootstrapPolicy.emptySchoolSetup(0,1,0,emptyMap(),true));assertFalse(AcademicBootstrapPolicy.emptySchoolSetup(0,0,0,mapOf("teacherAssignments" to listOf(mapOf("teacherId" to "t"))),false));assertFalse(AcademicBootstrapPolicy.emptySchoolSetup(0,0,0,emptyMap(),true))}
}
