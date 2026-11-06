package pk.edunexa.app.fees

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem

class FundSnapshotTest {
    private fun bill(type: String, net: Double, paid: Double = 0.0, session: String = "session", student: String = "ali", history: List<Map<String, Any?>> = emptyList()) =
        DiaryItem("$type-$session-$student", mapOf("studentId" to student, "academicSession" to session,
            "feeType" to type, "netPayable" to net, "paidAmount" to paid, "paymentHistory" to history,
            "month" to FeePolicy.month(), "dueDate" to "2999-12-31"))

    @Test fun paidTuitionDoesNotHideUnpaidAnnualFund() {
        val rows = listOf(bill(FeePolicy.TUITION, 2800.0, 2800.0), bill("Annual Fund", 4000.0))
        assertEquals("Paid", FeePolicy.displaySnapshot(rows, "ali", setOf("session")).first)
        assertEquals(FundSnapshot("Pending", 4000.0), FeePolicy.fundSnapshot(rows, "ali", setOf("session"), "Annual Fund"))
    }
    @Test fun receiptsReduceFundPendingAndFullPaymentClearsIt() {
        val partial = listOf(bill("Annual Fund", 4000.0, history = listOf(mapOf("amount" to 1500.0))))
        assertEquals(FundSnapshot("Partial", 2500.0), FeePolicy.fundSnapshot(partial, "ali", setOf("session"), "Annual Fund"))
        val paid = listOf(bill("Annual Fund", 4000.0, 4000.0, history = listOf(mapOf("amount" to 4000.0))))
        assertEquals(FundSnapshot("Paid", 0.0), FeePolicy.fundSnapshot(paid, "ali", setOf("session"), "Annual Fund"))
    }
    @Test fun fundScopeExcludesOtherStudentsSessionsAndFeeTypes() {
        val rows = listOf(bill("Annual Fund", 4000.0, session = "2026–2027"), bill("Annual Fund", 9000.0, session = "old"),
            bill("Annual Fund", 5000.0, student = "other"), bill("Other", 700.0), bill(FeePolicy.TUITION, 2800.0))
        assertEquals(FundSnapshot("Pending", 4000.0), FeePolicy.fundSnapshot(rows, "ali", setOf("session", "2026–2027"), "Annual Fund"))
        assertEquals(FundSnapshot("Pending", 700.0), FeePolicy.fundSnapshot(rows, "ali", setOf("session"), "Other"))
    }
    @Test fun missingBillOrSessionNeverClaimsPaid() {
        assertEquals(FundSnapshot("No saved bill", null), FeePolicy.fundSnapshot(emptyList(), "ali", setOf("session"), "Annual Fund"))
        assertEquals(FundSnapshot("Session not set", null), FeePolicy.fundSnapshot(listOf(bill("Annual Fund", 4000.0)), "ali", emptySet(), "Annual Fund"))
    }
}
