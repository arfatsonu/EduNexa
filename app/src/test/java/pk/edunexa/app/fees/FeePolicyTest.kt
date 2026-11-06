package pk.edunexa.app.fees
import org.junit.Assert.*
import org.junit.Test
class FeePolicyTest {
    private fun bill(net: Double = 2800.0, paid: Double = 0.0) = mapOf<String, Any?>("netPayable" to net, "paidAmount" to paid, "feeType" to FeePolicy.TUITION, "month" to FeePolicy.month(), "dueDate" to "2099-12-31")
    @Test fun noPaymentMeansUnpaid() { assertEquals("Unpaid", FeePolicy.status(bill())); assertEquals(2800.0, FeePolicy.balance(bill()),0.0) }
    @Test fun partialPaymentRetainsRemaining() { assertEquals("Partial", FeePolicy.status(bill(paid=1000.0))); assertEquals(1800.0,FeePolicy.balance(bill(paid=1000.0)),0.0) }
    @Test fun fullPaymentClearsBalance() { assertEquals("Paid", FeePolicy.status(bill(paid=2800.0))); assertEquals(0.0,FeePolicy.balance(bill(paid=2800.0)),0.0) }
    @Test(expected=IllegalArgumentException::class) fun overpaymentRejected() { FeePolicy.validatePayment(bill(),2800.01) }
    @Test(expected=IllegalArgumentException::class) fun zeroPaymentRejected() { FeePolicy.validatePayment(bill(),0.0) }
    @Test(expected=IllegalArgumentException::class) fun nonFinitePaymentRejected() { FeePolicy.validatePayment(bill(),Double.NaN) }
    @Test fun centsAvoidFloatingPointBalanceErrors() { assertEquals(0.1,FeePolicy.balance(bill(net=0.3,paid=0.2)),0.0); FeePolicy.validatePayment(bill(net=0.3,paid=0.2),0.1) }
    @Test fun historyPaymentsCannotBeErasedByStalePaidField() { assertEquals(1000.0,FeePolicy.paid(bill(paid=0.0)+("paymentHistory" to listOf(mapOf("amount" to 1000.0)))),0.0) }
    @Test fun lowerBillDoesNotErasePriorPayment() { assertEquals(3500.0,FeePolicy.paid(bill(net=2800.0,paid=3500.0)),0.0); assertEquals(0.0,FeePolicy.balance(bill(net=2800.0,paid=3500.0)),0.0) }
    @Test fun unpaidAnnualFundDoesNotChangePaidTuitionBadge() { assertEquals("Paid",FeePolicy.snapshot(listOf(bill(paid=2800.0),bill()+("feeType" to "Annual Fund"))).first) }
    @Test fun missingBillIsNotPaid() { assertEquals("No fee record",FeePolicy.snapshot(emptyList()).first) }
    @Test fun monthsAreComparedByYearAndMonth() { assertTrue(FeePolicy.monthKey("December 2025") < FeePolicy.monthKey("January 2026")) }
    @Test fun studentStatusExcludesOtherStudentsAndAcademicSessions() {
        fun row(id: String, student: String, session: String, paid: Double) = pk.edunexa.app.diary.DiaryItem(id, bill(paid=paid) + mapOf("studentId" to student, "academicSessionId" to session))
        val rows = listOf(row("own", "a", "current", 2800.0), row("otherStudent", "b", "current", 0.0), row("oldSession", "a", "past", 0.0))
        assertEquals("Paid" to 0.0, FeePolicy.studentSnapshot(rows, "a", setOf("current")))
    }
    @Test fun studentStatusAcceptsSavedSessionNameAndShowsRemainingAfterPartialPayment() {
        val row = pk.edunexa.app.diary.DiaryItem("bill", bill(paid=1000.0) + mapOf("studentId" to "a", "academicSessionId" to "2026–2027"))
        assertEquals("Pending" to 1800.0, FeePolicy.studentSnapshot(listOf(row), "a", setOf("session-id", "2026–2027")))
        assertEquals("No fee record" to 0.0, FeePolicy.studentSnapshot(listOf(row), "b", setOf("session-id", "2026–2027")))
    }
    @Test fun missingSessionShowsActualSavedDebtWithoutAssigningEnrollment() {
        val rows = listOf(pk.edunexa.app.diary.DiaryItem("own", bill(paid=1000.0) + mapOf("studentId" to "a", "academicSessionId" to "old")), pk.edunexa.app.diary.DiaryItem("other", bill() + mapOf("studentId" to "b")))
        val result = FeePolicy.displaySnapshot(rows, "a", emptySet())
        assertEquals("Session not set", result.first)
        assertEquals(1800.0, result.second!!, 0.0)
    }
    @Test fun missingBillAmountIsUnknownRatherThanZero() {
        assertNull(FeePolicy.displaySnapshot(emptyList(), "a", emptySet()).second)
        assertNull(FeePolicy.displaySnapshot(emptyList(), "a", setOf("current")).second)
    }
    @Test fun verifiedPaidBillCanShowZeroAndFundsNeverAlterTuitionDue() {
        val rows = listOf(pk.edunexa.app.diary.DiaryItem("paid", bill(paid=2800.0) + mapOf("studentId" to "a", "academicSessionId" to "current")), pk.edunexa.app.diary.DiaryItem("fund", bill() + mapOf("studentId" to "a", "academicSessionId" to "current", "feeType" to "Annual Fund")))
        assertEquals("Paid" to 0.0, FeePolicy.displaySnapshot(rows, "a", setOf("current")))
        assertEquals("Session not set" to 0.0, FeePolicy.displaySnapshot(rows, "a", emptySet()))
    }
}

