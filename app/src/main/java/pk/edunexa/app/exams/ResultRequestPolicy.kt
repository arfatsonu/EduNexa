package pk.edunexa.app.exams

/** The same request ID and revisions are reused after an uncertain server response. */
object ResultRequestPolicy {
    val actions = setOf("submit", "return", "lock", "publish", "unlock")
    fun prepare(schoolId: String, testId: String, action: String, reason: String,
                updatedAt: String, marksRevision: String, workflowRevision: String,
                operationId: String, pending: Map<String, String> = emptyMap()): Map<String, String> {
        require(listOf(schoolId, testId).all { it.isNotBlank() && '/' !in it && '\\' !in it }) { "Invalid school or test." }
        require(action in actions) { "Unsupported result action." }
        require(reason.trim().length <= 1000) { "Reason is too long." }
        require(action !in setOf("return", "unlock") || reason.trim().length >= 3) { "Return or unlock requires a reason (at least 3 characters)." }
        if (pending.isNotEmpty()) {
            check(pending["schoolId"] == schoolId && pending["testId"] == testId &&
                pending["action"] == action && pending["reason"] == reason.trim()) {
                "Retry the unresolved result request before starting another action."
            }
            check(pending.keys == setOf("schoolId", "testId", "action", "reason", "expectedUpdatedAt", "expectedMarksRevision", "expectedWorkflowRevision", "operationId") &&
                runCatching { java.util.UUID.fromString(pending["operationId"]) }.isSuccess) { "Saved result request is invalid. Review its server state." }
            return pending
        }
        java.util.UUID.fromString(operationId)
        return mapOf("schoolId" to schoolId, "testId" to testId, "action" to action,
            "reason" to reason.trim(), "expectedUpdatedAt" to updatedAt,
            "expectedMarksRevision" to marksRevision, "expectedWorkflowRevision" to workflowRevision,
            "operationId" to operationId)
    }
}
