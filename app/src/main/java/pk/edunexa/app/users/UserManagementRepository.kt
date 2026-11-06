package pk.edunexa.app.users

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
import pk.edunexa.app.diary.DiaryItem

object UserAccessPolicy {
    fun eligible(role: String, status: String) = when(role) { "teacher" -> status.equals("Active", true); "student" -> !status.equals("Inactive", true); else -> false }
    fun effectiveActive(role: String, status: String, disabled: Boolean) = eligible(role, status) && !disabled
    fun validateTarget(actorUid: String, uid: String, role: String) { require(uid.isNotBlank() && actorUid != uid && role in setOf("teacher", "student")) { "Only other Teacher/Student logins can be managed." } }
    fun status(role: String, profileStatus: String, linked: Boolean, active: Boolean, disabled: Boolean, broken: Boolean): String = when { broken -> "Link needs review"; !linked -> "Missing Login"; role == "admin" -> if(active) "Admin Active" else "Admin Inactive"; !eligible(role,profileStatus) -> "Record Inactive"; disabled || !active -> "Portal Disabled"; else -> "Portal Active" }
}
data class ManagedAccount(val key: String, val uid: String?, val role: String, val profileId: String, val name: String, val idNumber: String, val email: String, val detail: String, val profileStatus: String, val active: Boolean, val disabled: Boolean, val broken: Boolean, val original: Map<String, Any?>) {
    val status get() = UserAccessPolicy.status(role, profileStatus, uid != null, active, disabled, broken)
    val manageable get() = uid != null && role in setOf("teacher", "student") && !broken
}
object ManagedAccountPolicy {
    fun build(users: List<DiaryItem>, teachers: List<DiaryItem>, students: List<DiaryItem>): List<ManagedAccount> {
        val result = mutableListOf<ManagedAccount>(); val consumed = mutableSetOf<String>()
        listOf("teacher" to teachers, "student" to students).forEach { (role, profiles) -> profiles.forEach profileLoop@ { profile ->
            val links = users.filter { it.text("role") == role && it.text("profileId") == profile.id }
            consumed.addAll(links.map { it.id })
            if (profile.raw["isDeleted"] == true) return@profileLoop
            val user = links.singleOrNull(); val authUid = profile.text("authUid")
            val broken = links.size > 1 || (authUid.isNotBlank() && user?.id != authUid)
            result += ManagedAccount("$role:${profile.id}", user?.id, role, profile.id, profile.text(if(role == "teacher") "name" else "fullName"), profile.text(if(role == "teacher") "employeeId" else "admissionNo"), user?.text("email").orEmpty().ifBlank { profile.text("portalEmail") }, if(role == "teacher") profile.text("qualification") else "${profile.text("className")} • ${profile.text("section")}", profile.text("status"), user?.raw?.get("active") == true, user?.raw?.get("portalDisabled") == true, broken, user?.raw.orEmpty())
        } }
        users.filter { it.id !in consumed }.forEach { user ->
            val admin = user.text("role") == "admin"
            result += ManagedAccount("user:${user.id}", user.id, user.text("role"), user.text("profileId"), user.text("name").ifBlank { "Linked account" }, user.text("idNumber"), user.text("email"), if(admin) "School administrator" else "Profile link is missing or role is invalid", if(admin) "Admin" else "Missing Profile", user.raw["active"] == true, user.raw["portalDisabled"] == true, !admin, user.raw)
        }
        return result.sortedWith(compareBy<ManagedAccount> { it.role }.thenBy { it.name.lowercase() })
    }
}
class UserManagementRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private suspend fun authorize() {
        check(actor.role == PortalRole.ADMIN && FirebaseAuth.getInstance().currentUser?.uid == actor.uid)
        val admin = db.collection("users").document(actor.uid).get(Source.SERVER).awaitAccess()
        check(admin.getBoolean("active") == true && admin.getString("role") == "admin" && admin.getString("schoolId") == actor.schoolId && admin.getBoolean("portalDisabled") != true) { "Active school Admin required." }
    }
    suspend fun load(): List<ManagedAccount> {
        authorize(); val root = db.collection("schools").document(actor.schoolId)
        val users = db.collection("users").whereEqualTo("schoolId", actor.schoolId).get(Source.SERVER).awaitAccess().documents.map { DiaryItem(it.id,it.data.orEmpty()) }
        suspend fun profiles(collection: String) = root.collection(collection).get(Source.SERVER).awaitAccess().documents.map { DiaryItem(it.id,it.data.orEmpty()) }
        return ManagedAccountPolicy.build(users, profiles("teachers"), profiles("students"))
    }
    suspend fun setDisabled(account: ManagedAccount, disabled: Boolean) {
        authorize(); check(account.manageable); UserAccessPolicy.validateTarget(actor.uid, account.uid!!, account.role)
        val links = db.collection("users").whereEqualTo("schoolId", actor.schoolId).get(Source.SERVER).awaitAccess().documents.filter { it.getString("role") == account.role && it.getString("profileId") == account.profileId }
        check(links.size == 1 && links.single().id == account.uid) { "Duplicate or changed login links. Refresh and review." }
        db.runTransaction { tx ->
            val admin = tx.get(db.collection("users").document(actor.uid))
            val ref = db.collection("users").document(account.uid); val user = tx.get(ref)
            val profile = tx.get(db.collection("schools").document(actor.schoolId).collection(if(account.role == "teacher") "teachers" else "students").document(account.profileId))
            check(admin.getBoolean("active") == true && admin.getString("role") == "admin" && admin.getString("schoolId") == actor.schoolId)
            check(user.exists() && profile.exists() && profile.getBoolean("isDeleted") != true && user.getString("schoolId") == actor.schoolId && user.getString("role") == account.role && user.getString("profileId") == account.profileId) { "Login link changed. Refresh." }
            check(profile.getString("authUid").isNullOrBlank() || profile.getString("authUid") == account.uid) { "Profile linked to a different login." }
            check(listOf("active", "portalDisabled").all { user.get(it) == account.original[it] }) { "Access changed on another device. Refresh." }
            val status = profile.getString("status").orEmpty()
            tx.update(ref, mapOf("portalDisabled" to disabled, "active" to UserAccessPolicy.effectiveActive(account.role,status,disabled), "portalAccessUpdatedBy" to actor.uid, "portalAccessUpdatedByName" to actor.name, "portalAccessUpdatedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
        }.awaitAccess()
    }
}
private suspend fun <T> Task<T>.awaitAccess(): T = suspendCancellableCoroutine { c -> addOnSuccessListener { if(c.isActive) c.resume(it) }; addOnFailureListener { if(c.isActive) c.resumeWithException(it) } }

