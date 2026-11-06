package pk.edunexa.app.operations

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import java.util.UUID

/** The secondary Auth instance never replaces the Owner's signed-in session. */
class OwnerAdminAccountRepository(private val actor: PortalSession) {
    private val owner = OwnerRepository(actor)
    private suspend fun <T> verified(schoolId: String, admin: DiaryItem, email: String, password: String, action: suspend (com.google.firebase.auth.FirebaseUser) -> T): T {
        owner.authorize()
        OwnerSchoolPolicy.admin(admin.raw, admin.id, schoolId, actor.uid)
        require(password.isNotBlank()) { "Enter the Admin's current password, or use password reset." }
        val primary = FirebaseApp.getInstance()
        val app = FirebaseApp.initializeApp(primary.applicationContext, primary.options, "admin-manage-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app)
        try {
            val user = auth.signInWithEmailAndPassword(OwnerSchoolPolicy.email(email), password).awaitOperation().user ?: error("Admin login could not be verified.")
            check(user.uid == admin.id && user.getIdToken(true).awaitOperation().claims["platformOwner"] != true) { "These credentials belong to a different account. No login details changed." }
            val fresh = owner.admins(schoolId).firstOrNull { it.id == admin.id } ?: error("Admin account no longer exists.")
            OwnerSchoolPolicy.admin(fresh.raw, fresh.id, schoolId, actor.uid)
            return action(user)
        } finally { auth.signOut(); app.delete() }
    }
    suspend fun reset(schoolId: String, admin: DiaryItem) {
        owner.authorize()
        val fresh = owner.admins(schoolId).firstOrNull { it.id == admin.id } ?: error("Admin no longer exists.")
        OwnerSchoolPolicy.admin(fresh.raw, fresh.id, schoolId, actor.uid)
        FirebaseAuth.getInstance().sendPasswordResetEmail(OwnerSchoolPolicy.email(fresh.text("email"))).awaitOperation()
    }
    suspend fun changePassword(schoolId: String, admin: DiaryItem, email: String, current: String, new: String, confirmation: String) {
        OwnerSchoolPolicy.password(new, confirmation)
        verified(schoolId, admin, email, current) { it.updatePassword(new).awaitOperation() }
    }
    suspend fun requestEmail(schoolId: String, admin: DiaryItem, email: String, password: String, newEmail: String) {
        val next = OwnerSchoolPolicy.email(newEmail)
        require(next != OwnerSchoolPolicy.email(email)) { "Enter a different email address." }
        verified(schoolId, admin, email, password) { it.verifyBeforeUpdateEmail(next).awaitOperation() }
    }
    suspend fun syncEmail(schoolId: String, admin: DiaryItem, email: String, password: String) {
        verified(schoolId, admin, email, password) { user ->
            owner.saveAdmin(schoolId, admin, admin.text("name"), user.email ?: error("Verified login email missing."))
        }
    }
}
