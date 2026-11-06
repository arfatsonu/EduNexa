package pk.edunexa.app.activity

import pk.edunexa.app.diary.DiaryPolicy
import java.time.Instant
import java.util.UUID

/** Recent operational history, stored atomically with its source record. Never contains passwords. */
object ActivityHistory {
    val fields=setOf("fullName","name","status","classId","sectionId","rollNo","admissionNo","monthlyFee","annualFund","otherFunds","guardianPhone","remarks","note","followUpComment","isDeleted","paidAmount","paymentHistory","schoolName","campusName","schoolShortName","schoolStartTime","autoLateEnabled","lateGraceMinutes","phone","email","attendanceMinimumPercent","lateCountsAsPresent","excludeLeaveFromAttendance","principalName","address","tagline","logoBase64","portalEmail","schoolLogo","brandPrimary","brandAccent","brandBackground","launcherIcon")
    fun patch(before: Map<String,Any?>, after: Map<String,Any?>, uid: String, name: String, role: String, action: String, target: String, now: String=Instant.now().toString()): Map<String,Any?> {
        require(uid.isNotBlank()) { "Signed-in account required for activity history." }
        val changed=after.keys.filter { it in fields && before[it] != after[it] }.sorted()
        if(changed.isEmpty()) return emptyMap()
        val canonicalTime=runCatching { Instant.parse(now).toString() }.getOrElse { Instant.now().toString() }
        val event=mapOf("id" to UUID.randomUUID().toString(),"at" to canonicalTime,"actorUid" to uid,"actorName" to name.ifBlank { uid },"role" to role,"action" to action,"target" to target.take(160),"fields" to changed)
        return mapOf("activityHistory" to (DiaryPolicy.maps(before["activityHistory"]).takeLast(99)+event),"lastEditedByUid" to uid,"lastEditedByName" to name,"lastEditedAt" to canonicalTime)
    }
}
