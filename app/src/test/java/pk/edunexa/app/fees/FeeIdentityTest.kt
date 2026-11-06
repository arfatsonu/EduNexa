package pk.edunexa.app.fees
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
class FeeIdentityTest {
    private val bill = DiaryItem("fee", mapOf("studentId" to "a", "studentName" to "Ali", "admissionNo" to "old", "classId" to "c", "sectionId" to "s", "academicSessionId" to "current", "rollNo" to "01", "netPayable" to 2800.0, "paidAmount" to 2800.0, "paymentHistory" to listOf(mapOf("amount" to 2800.0))))
    private fun student(id: String = "a", session: String = "current") = DiaryItem(id, mapOf("name" to "Ali", "admissionNo" to "new", "classId" to "c", "sectionId" to "s", "academicSessionId" to session, "rollNo" to "20"))
    @Test fun currentIdentityUsesStableIdAndPreservesMoney() {
        val shown = FeeIdentity.display(bill, listOf(student("b"), student()))
        assertEquals("20", shown.text("rollNo"))
        assertEquals("new", shown.text("admissionNo"))
        for(key in listOf("netPayable", "paidAmount", "paymentHistory", "studentId")) assertEquals(bill.raw[key], shown.raw[key])
        assertEquals("01", bill.text("rollNo"))
    }
    @Test fun duplicateNameCannotReassignBill() {
        assertEquals(bill, FeeIdentity.display(bill, listOf(student("b"))))
        assertNull(FeeIdentity.profile(bill, listOf(student("b"))))
    }
    @Test fun historicalSessionKeepsSavedRoll() {
        assertEquals("01", FeeIdentity.display(bill, listOf(student(session = "past"))).text("rollNo"))
    }
    @Test fun configuredSessionNameAndIdUseSameCurrentRoll() {
        val shown = FeeIdentity.display(bill, listOf(student(session = "2026–2027")), listOf(setOf("current", "2026–2027")))
        assertEquals("20", shown.text("rollNo"))
    }
    @Test fun removedProfileStillHasExplicitStableIdentity() {
        val removed = student().copy(raw = student().raw + ("isDeleted" to true))
        assertEquals(true, FeeIdentity.profile(bill, listOf(removed))?.raw?.get("isDeleted"))
        assertEquals("new", FeeIdentity.display(bill, listOf(removed)).text("admissionNo"))
    }
}
