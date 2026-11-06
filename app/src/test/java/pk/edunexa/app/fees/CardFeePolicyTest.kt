package pk.edunexa.app.fees
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.attendance.CardAttendancePolicy
import pk.edunexa.app.diary.DiaryItem
class CardFeePolicyTest {
    private val qr=CardAttendancePolicy.encode("school-a","p")
    private val pupil=DiaryItem("p",mapOf("status" to "Active","admissionNo" to "008"))
    @Test fun existingAttendanceQrOpensExactStudentWithoutMutatingFeesOrAttendance(){assertEquals(pupil,CardFeePolicy.validate("school-a",qr,pupil));assertEquals(mapOf("status" to "Active","admissionNo" to "008"),pupil.raw)}
    @Test fun wrongSchoolMissingDeletedOrInactiveStudentAreRejected(){listOf({CardFeePolicy.validate("school-b",qr,pupil)},{CardFeePolicy.validate("school-a",qr,null)},{CardFeePolicy.validate("school-a",qr,pupil.copy(raw=pupil.raw+("isDeleted" to true)))},{CardFeePolicy.validate("school-a",qr,pupil.copy(raw=pupil.raw+("status" to "Inactive")))},{CardFeePolicy.validate("school-a",qr,pupil.copy(id="other"))}).forEach{assertTrue(runCatching(it).isFailure)}}
    @Test fun onLeaveStudentCanReviewRealDues(){assertEquals("On Leave",CardFeePolicy.validate("school-a",qr,pupil.copy(raw=pupil.raw+("status" to "On Leave"))).text("status"))}
}
