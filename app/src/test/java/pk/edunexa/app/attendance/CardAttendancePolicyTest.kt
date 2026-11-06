package pk.edunexa.app.attendance

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.teachers.TeacherPortalPolicy
import com.google.zxing.*
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.HybridBinarizer
import java.util.Date

class CardAttendancePolicyTest {
    private val admin=PortalSession("uid","Admin","admin@example.edu",PortalRole.ADMIN,"school-001")
    private val teacher=PortalSession("tuid","Teacher","teacher@example.edu",PortalRole.TEACHER,"school-001",profileId="t1")
    private val pupil=DiaryItem("student-008",mapOf("schoolId" to "school-001","fullName" to "Ali","status" to "Active","classId" to "c1","sectionId" to "s1","academicSessionId" to "session1"))
    private val config=mapOf("sessions" to listOf(mapOf("id" to "session1","name" to "2026-27","status" to "Active","isCurrent" to true)),"classes" to listOf(mapOf("id" to "c1","name" to "Class 1","isActive" to true)),"sections" to listOf(mapOf("id" to "s1","classId" to "c1","name" to "A","isActive" to true)))
    private val card=AttendanceCard("school-001","student-008")
    private fun denied(block: () -> Unit) { try { block();fail("Expected rejection") } catch(e: IllegalArgumentException) {} catch(e: IllegalStateException) {} }
    @Test fun actualQrImageRoundTripsToScopedCardWithoutNameOrEmail() {
        val value=CardAttendancePolicy.encode(card.schoolId,card.studentId)
        val matrix=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,640,640,mapOf(EncodeHintType.MARGIN to 4))
        val pixels=IntArray(640*640) { if(matrix[it%640,it/640]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val decoded=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(640,640,pixels)))).text
        assertEquals(card,CardAttendancePolicy.decode(decoded));assertFalse(value.contains("Ali"));assertFalse(value.contains("example.edu"))
    }
    @Test fun malformedQrAndPathInjectionAreRejected() {
        for(value in listOf("008","https://example.com","EDUNEXA:2:school-001:student-008","EDUNEXA:1:school-001:../users","EDUNEXA:1:school-001:x:y","EDUNEXA:1:school-001:","EDUNEXA:1:school-001:..","EDUNEXA:1:school-001:a\\b")) denied { CardAttendancePolicy.decode(value) }
    }
    @Test fun adminAcceptsCurrentActiveStudentOnly() {
        assertEquals("session1",CardAttendancePolicy.scope(admin,card,pupil,config).sessionId)
        denied { CardAttendancePolicy.scope(admin,AttendanceCard("other",pupil.id),pupil,config) };denied { CardAttendancePolicy.scope(admin,card,null,config) }
        for(extra in listOf(mapOf("status" to "Inactive"),mapOf("isDeleted" to true),mapOf("academicSessionId" to "old"),mapOf("schoolId" to "other"))) denied { CardAttendancePolicy.scope(admin,card,DiaryItem(pupil.id,pupil.raw+extra),config) }
    }
    @Test fun missingSessionAndInactiveClassOrSectionDoNotMark() {
        for(key in listOf("sessions","classes","sections")) denied { CardAttendancePolicy.scope(admin,card,pupil,config+mapOf(key to emptyList<Any>())) }
        denied { CardAttendancePolicy.scope(admin,card,pupil,config+mapOf("sessions" to listOf(mapOf("id" to "session1","isCurrent" to true,"status" to "Upcoming")))) }
    }
    @Test fun teacherCannotGainAdminScannerAccess() {
        denied { CardAttendancePolicy.scope(teacher,card,pupil,config) }
        denied { CardAttendancePolicy.staff(teacher,mapOf("active" to true,"role" to "teacher","schoolId" to "school-001","profileId" to "t1"),"tuid") }
    }
    @Test fun studentRoleCanNeverRunScanner() {
        denied { CardAttendancePolicy.scope(admin.copy(role=PortalRole.STUDENT),card,pupil,config) }
        denied { CardAttendancePolicy.staff(admin.copy(role=PortalRole.STUDENT),mapOf("active" to true,"role" to "student","schoolId" to "school-001"),"uid") }
    }
    @Test fun authorizationRechecksUidRoleSchoolAndDisabledState() {
        val user=mapOf<String,Any?>("active" to true,"portalDisabled" to false,"role" to "admin","schoolId" to "school-001")
        CardAttendancePolicy.staff(admin,user,"uid");denied { CardAttendancePolicy.staff(admin,user,"other") }
        for(extra in listOf(mapOf("active" to false),mapOf("portalDisabled" to true),mapOf("schoolId" to "other"),mapOf("role" to "student"))) denied { CardAttendancePolicy.staff(admin,user+extra,"uid") }
        denied { CardAttendancePolicy.staff(teacher,mapOf("active" to true,"role" to "teacher","schoolId" to "school-001","profileId" to "other"),"tuid") }
    }
    @Test fun existingAttendanceNeverBecomesPresentAutomatically() {
        for(status in listOf("Present","Absent","Leave","Late")) assertEquals(status,CardAttendancePolicy.existing(listOf(DiaryItem("saved",mapOf("status" to status))))!!.text("status"))
        assertEquals("Present",CardAttendancePolicy.existing(listOf(DiaryItem("legacy",mapOf("status" to "present"))))!!.text("status"))
        assertNull(CardAttendancePolicy.existing(emptyList()));assertNull(CardAttendancePolicy.existing(listOf(DiaryItem("pending",mapOf("status" to "Unmarked")))))
    }
    @Test fun qrAndManualAttendanceUseTheSameCanonicalDocumentId() {
        val scope=CardAttendancePolicy.scope(admin,card,pupil,config)
        val id=TeacherPortalPolicy.attendanceId(scope.sessionId,"2026-10-19","c1","s1",pupil.id)
        assertEquals("session1__2026-10-19__c1__s1__student-008",id)
        assertEquals(id,TeacherPortalPolicy.attendanceId(scope.sessionId,"2026-10-19","c1","s1",pupil.id));assertNotEquals(id,TeacherPortalPolicy.attendanceId(scope.sessionId,"2026-10-20","c1","s1",pupil.id))
    }
    @Test fun schoolDateUsesPakistanMidnightRegardlessOfPhoneTimezone() {
        val previous=java.util.TimeZone.getDefault()
        try { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));assertEquals("2026-10-20",CardAttendancePolicy.today(Date.from(java.time.Instant.parse("2026-10-19T19:01:00Z")))) } finally { java.util.TimeZone.setDefault(previous) }
    }
    @Test fun activeSessionWithoutCurrentFlagMatchesExistingAdmissionFlow() {
        val legacy=config+mapOf("sessions" to listOf(mapOf("id" to "session1","name" to "2026-27","status" to "Active")))
        val scope=CardAttendancePolicy.scope(admin,card,pupil,legacy)
        assertEquals("session1",scope.sessionId)
        assertEquals(pk.edunexa.app.students.EnrollmentPolicy.currentSession(legacy)["id"],scope.sessionId)
    }
    @Test fun activeSessionStatusIsCaseInsensitiveLikeDashboard() {
        for(status in listOf("active","ACTIVE","Active")) {
            val legacy=config+mapOf("sessions" to listOf(mapOf("id" to "session1","name" to "2026-27","status" to status,"isCurrent" to false)))
            assertEquals("session1",CardAttendancePolicy.scope(admin,card,pupil,legacy).sessionId)
        }
    }
    @Test fun explicitCurrentIsPreservedAndClosedCurrentDoesNotFallBack() {
        val other=mapOf("id" to "other","name" to "Other year","status" to "Active")
        val current=mapOf("id" to "session1","name" to "2026-27","status" to "Active","isCurrent" to true)
        assertEquals("session1",CardAttendancePolicy.scope(admin,card,pupil,config+mapOf("sessions" to listOf(other,current))).sessionId)
        denied { CardAttendancePolicy.scope(admin,card,pupil,config+mapOf("sessions" to listOf(other,current+mapOf("status" to "Closed")))) }
        denied { CardAttendancePolicy.scope(admin,card,pupil,config+mapOf("sessions" to listOf(other))) }
    }

    @Test fun explicitCurrentWithoutLegacyStatusUsesSavedCurrentSession() {
        val legacy=config+mapOf("sessions" to listOf(mapOf("id" to "session1","name" to "2026-27","isCurrent" to true)))
        assertEquals("session1",CardAttendancePolicy.scope(admin,card,pupil,legacy).sessionId)
        denied { CardAttendancePolicy.scope(admin,card,pupil,config+mapOf("sessions" to listOf(mapOf("id" to "session1","name" to "2026-27")))) }
    }

}
