package pk.edunexa.app.workflow
import org.junit.Assert.*
import org.junit.Test
class WorkflowPolicyTest {
    @Test fun emptySchoolDoesNotGetDemoSetupOrFalseCompletion(){val steps=WorkflowPolicy.setup(emptyMap(),emptyMap(),0,0);assertEquals(6,steps.size);assertTrue(steps.none{it.complete})}
    @Test fun setupNeedsActiveSessionAndActiveClassSectionLink(){val settings=mapOf("schoolName" to "School","campusName" to "Main","schoolShortName" to "ANS");val config=mapOf("sessions" to listOf(mapOf("id" to "s","status" to "Closed","isCurrent" to true)),"classes" to listOf(mapOf("id" to "c","isActive" to true)),"sections" to listOf(mapOf("id" to "sec","classId" to "other","isActive" to true)));val steps=WorkflowPolicy.setup(settings,config,2,10);assertTrue(steps.first().complete);assertFalse(steps[1].complete);assertFalse(steps[2].complete);assertTrue(steps[4].complete);assertTrue(steps[5].complete)}
    @Test fun paymentRetryMustMatchOriginalPayload(){val history=listOf(mapOf("id" to "id","amount" to 300.0,"paymentMethod" to "Cash","notes" to "Partial"));WorkflowPolicy.verifyPaymentRetry(history,"id",300.0,"Cash","Partial");WorkflowPolicy.verifyPaymentRetry(history,"new",300.0,"Cash","Partial");assertTrue(runCatching{WorkflowPolicy.verifyPaymentRetry(history,"id",400.0,"Cash","Partial")}.isFailure);assertTrue(runCatching{WorkflowPolicy.verifyPaymentRetry(history,"id",300.0,"Bank","Partial")}.isFailure)}
}
