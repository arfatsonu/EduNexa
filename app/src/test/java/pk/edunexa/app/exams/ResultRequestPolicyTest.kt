package pk.edunexa.app.exams
import org.junit.Assert.*
import org.junit.Test
class ResultRequestPolicyTest {
    private fun request(action: String = "publish", reason: String = "Reviewed", pending: Map<String,String> = emptyMap()) =
        ResultRequestPolicy.prepare("school", "test", action, reason, "new", "new-marks", "new-flow", "12345678-1234-4234-8234-123456789abc", pending)
    @Test fun retriesRetainTheExactOriginalRevisionsAndOperation() {
        val saved=request().toMutableMap().apply { put("expectedWorkflowRevision","old-flow"); put("expectedMarksRevision","old-marks") }
        assertEquals(saved,request(pending=saved))
    }
    @Test fun anotherActionOrReasonCannotOverwriteAnUncertainRequest() {
        val saved=request()
        assertThrows(IllegalStateException::class.java) { request("unlock",pending=saved) }
        assertThrows(IllegalStateException::class.java) { request(reason="Different",pending=saved) }
    }
    @Test fun anotherSchoolOrTestCannotReuseTheRequest() {
        assertThrows(IllegalStateException::class.java) { request(pending=request()+mapOf("schoolId" to "another")) }
        assertThrows(IllegalStateException::class.java) { request(pending=request()+mapOf("testId" to "another")) }
    }
    @Test fun unknownActionsAndMalformedRequestsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { request("delete") }
        assertThrows(IllegalStateException::class.java) { request(pending=request()+mapOf("operationId" to "bad")) }
    }
}
