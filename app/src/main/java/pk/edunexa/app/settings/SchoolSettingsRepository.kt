package pk.edunexa.app.settings

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession

object AttendancePolicy {
    fun rate(present: Int, absent: Int, late: Int, leave: Int, lateCounts: Boolean, excludeLeave: Boolean): Double? {
        require(listOf(present, absent, late, leave).all { it >= 0 })
        val denominator = present.toLong() + absent + late + if(excludeLeave) 0 else leave
        return if(denominator == 0L) null else (present.toLong() + if(lateCounts) late else 0) * 100.0 / denominator
    }
}
object SchoolSettingsPolicy {
    val profileFields = linkedMapOf("schoolName" to "School Name", "campusName" to "Campus / City", "schoolShortName" to "School Short Form (ANS / ANPS)", "tagline" to "Tagline", "affiliation" to "Affiliation / Board", "principalName" to "Principal / Head Name", "email" to "Official Email", "phone" to "Phone", "whatsapp" to "WhatsApp", "address" to "Address")
    val attendanceFields = setOf("attendanceMinimumPercent", "lateCountsAsPresent", "excludeLeaveFromAttendance", "autoLateEnabled", "schoolStartTime", "lateGraceMinutes")
    fun editable(raw: Map<String, Any?>) = profileFields.keys.associateWith { key -> raw[key]?.toString().orEmpty().ifBlank { when(key) { "schoolName" -> "EduNexa"; "campusName" -> ""; else -> "" } } } + mapOf("attendanceMinimumPercent" to ((raw["attendanceMinimumPercent"] as? Number)?.toDouble() ?: 75.0), "lateCountsAsPresent" to (raw["lateCountsAsPresent"] as? Boolean ?: true), "excludeLeaveFromAttendance" to (raw["excludeLeaveFromAttendance"] as? Boolean ?: true)) + mapOf("autoLateEnabled" to (raw["autoLateEnabled"] == true), "schoolStartTime" to raw["schoolStartTime"]?.toString().orEmpty(), "lateGraceMinutes" to ((raw["lateGraceMinutes"] as? Number)?.toInt() ?: 0)) + BrandingPolicy.editable(raw)
    fun validateProfile(input: Map<String, Any?>): Map<String, Any?> {
        var result = profileFields.keys.associateWith { input[it]?.toString().orEmpty().trim() }
        require(result["schoolName"].orEmpty().isNotBlank() && result["campusName"].orEmpty().isNotBlank()) { "School name and campus required." }
        require(result.all { (key, value) -> value.length <= if(key == "address") 1000 else 200 }) { "Text is too long (address max 1,000; other fields max 200)." }
        val short = result["schoolShortName"].orEmpty()
        if (short.isNotBlank()) result = result + ("schoolShortName" to pk.edunexa.app.auth.StudentLoginIdentity.shortName(short))
        val email = result["email"].orEmpty()
        require(email.isBlank() || email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Valid official email required." }
        listOf("phone", "whatsapp").forEach { key -> val value = result[key].orEmpty(); require(value.isBlank() || (value.count { it.isDigit() } in 7..15 && value.all { it.isDigit() || it in "+ -()" })) { "Valid $key number required." } }
        return result
    }
    fun validateAttendance(input: Map<String, Any?>): Map<String, Any?> {
        val threshold = (input["attendanceMinimumPercent"] as? Number)?.toDouble() ?: error("Minimum percentage required.")
        require(threshold.isFinite() && threshold in 0.0..100.0) { "Minimum percentage must be 0 to 100." }
        require(input["lateCountsAsPresent"] is Boolean && input["excludeLeaveFromAttendance"] is Boolean)
        pk.edunexa.app.attendance.ArrivalPolicy.validate(input)
        return mapOf("autoLateEnabled" to (input["autoLateEnabled"] == true), "schoolStartTime" to input["schoolStartTime"]?.toString().orEmpty().trim(), "lateGraceMinutes" to ((input["lateGraceMinutes"] as? Number)?.toInt() ?: 0), "attendanceMinimumPercent" to threshold, "lateCountsAsPresent" to input["lateCountsAsPresent"], "excludeLeaveFromAttendance" to input["excludeLeaveFromAttendance"])
    }
    fun mergeSection(current: Map<String, Any?>, original: Map<String, Any?>, patch: Map<String, Any?>): Map<String, Any?> {
        require(patch.keys.all { it in profileFields.keys || it in attendanceFields || it in BrandingPolicy.fields })
        check(patch.keys.all { current[it] == original[it] || current[it] == patch[it] }) { "These settings changed on another device. Reload before saving." }
        return current + patch
    }
}
class SchoolSettingsRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val ref get() = db.collection("schools").document(actor.schoolId)
    private suspend fun authorize() {
        check(actor.role == PortalRole.ADMIN && FirebaseAuth.getInstance().currentUser?.uid == actor.uid) { "Active Admin session required." }
        val user = db.collection("users").document(actor.uid).get(Source.SERVER).awaitSettings()
        check(user.getBoolean("active") == true && user.getString("role") == "admin" && user.getString("schoolId") == actor.schoolId)
    }
    suspend fun load(): Map<String, Any?> { authorize(); val school = ref.get(Source.SERVER).awaitSettings(); check(school.exists()); @Suppress("UNCHECKED_CAST") return school.get("settings") as? Map<String, Any?> ?: emptyMap() }
    suspend fun save(original: Map<String, Any?>, input: Map<String, Any?>, profile: Boolean): Map<String, Any?> {
        val patch = if(profile) SchoolSettingsPolicy.validateProfile(input) else SchoolSettingsPolicy.validateAttendance(input)
        return savePatch(original, patch)
    }
    suspend fun saveBranding(original: Map<String, Any?>, input: Map<String, Any?>): Map<String, Any?> = savePatch(original, BrandingPolicy.validate(input))
    private suspend fun savePatch(original: Map<String, Any?>, patch: Map<String, Any?>): Map<String, Any?> {
        authorize()
        return db.runTransaction { tx ->
            val school = tx.get(ref); check(school.exists())
            @Suppress("UNCHECKED_CAST") val current = school.get("settings") as? Map<String, Any?> ?: emptyMap()
            val merged = SchoolSettingsPolicy.mergeSection(current, original, patch)
            // One nested map is merged deliberately, preserving unknown settings and all other school fields.
            tx.update(ref, mapOf("settings" to merged, "settingsUpdatedAt" to FieldValue.serverTimestamp()) + pk.edunexa.app.activity.ActivityHistory.patch(school.data.orEmpty() + current, patch, actor.uid,actor.name,actor.role.value,"School settings updated",actor.schoolId))
            merged
        }.awaitSettings()
    }
}
private suspend fun <T> Task<T>.awaitSettings(): T = suspendCancellableCoroutine { c -> addOnSuccessListener { if(c.isActive) c.resume(it) }; addOnFailureListener { if(c.isActive) c.resumeWithException(it) } }

