package pk.edunexa.app.students

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.fees.FeePolicy

class EnrollmentPolicyTest {
    private val student=mapOf<String,Any?>("id" to "s1","fullName" to "Usman Qamar","status" to "Active","classId" to "c","sectionId" to "a","rollNo" to "01","monthlyFee" to 2800.0,"annualFund" to 4000.0,"academicSessionId" to "current")
    @Test fun activeSessionFallbackMatchesDashboardAndExplicitCurrentWins() {
        val active=mapOf<String,Any?>("id" to "current","status" to "Active")
        assertEquals(active,EnrollmentPolicy.currentSession(mapOf("sessions" to listOf(active))))
        val flagged=mapOf<String,Any?>("id" to "flag","isCurrent" to true)
        assertEquals(flagged,EnrollmentPolicy.currentSession(mapOf("sessions" to listOf(active,flagged))))
    }
    @Test fun rollValidationIgnoresRemovedStudentsAndUsesSessionAndNumericValue() {
        assertTrue(EnrollmentPolicy.duplicateRoll(student+("rollNo" to "1"),student,setOf("current")))
        assertFalse(EnrollmentPolicy.duplicateRoll(student+("isDeleted" to true),student,setOf("current")))
        assertFalse(EnrollmentPolicy.duplicateRoll(student+("status" to "Inactive"),student,setOf("current")))
        assertFalse(EnrollmentPolicy.duplicateRoll(student+("academicSessionId" to "old"),student,setOf("current")))
        assertFalse(EnrollmentPolicy.duplicateRoll(student+("sectionId" to "b"),student,setOf("current")))
    }
    @Test fun createsTuitionAndAnnualFundSeparatelyWithoutPayments() {
        val bills=EnrollmentPolicy.initialBills(student,"school","current","now")
        assertEquals(2,bills.size)
        assertEquals(2800.0,bills.first { it["feeType"]==FeePolicy.TUITION }["netPayable"])
        assertEquals(4000.0,bills.first { it["feeType"]=="Annual Fund" }["netPayable"])
        assertTrue(bills.all { it["paidAmount"]==0.0&&it["academicSessionId"]=="current" })
        assertEquals(bills.map { it["id"] },EnrollmentPolicy.initialBills(student,"school","current","retry").map { it["id"] })
        assertTrue(EnrollmentPolicy.initialBills(student+("status" to "Inactive"),"school","current","now").isEmpty())
    }
    @Test fun annualFundIsNotReducedByTuitionConcession() {
        val bills=EnrollmentPolicy.initialBills(student+("concession" to "10%"),"school","current","now")
        assertEquals(2520.0,bills.first { it["feeType"]==FeePolicy.TUITION }["netPayable"])
        assertEquals(4000.0,bills.first { it["feeType"]=="Annual Fund" }["netPayable"])
        assertEquals("STU-1568",EnrollmentPolicy.admission(1568))
    }
}
