package pk.edunexa.app.auth

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import java.util.UUID

// A separate Auth instance keeps the Admin signed in. Only the Admin's Firestore
// session links the new account; no passwords or privileged credentials are saved.
class ProfileLoginRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    fun create(schoolId: String, role: String, profileId: String, login: GeneratedLogin,
               onResult: (Result<Unit>) -> Unit) {
        require(role == "student" || role == "teacher")
        val ref = db.collection("schools").document(schoolId)
            .collection(if (role == "student") "students" else "teachers").document(profileId)
        ref.get(Source.SERVER).addOnSuccessListener { profile ->
            if (!profile.exists() || profile.getBoolean("isDeleted") == true) {
                onResult(Result.failure(IllegalStateException("This profile is unavailable or already has a login.")))
                return@addOnSuccessListener
            }
            val linkedUid = profile.getString("authUid").orEmpty()
            if (linkedUid.isNotBlank()) {
                verifyExisting(schoolId, role, profileId, linkedUid, login, onResult)
                return@addOnSuccessListener
            }
            db.collection("users").whereEqualTo("schoolId", schoolId).whereEqualTo("profileId", profileId).get(Source.SERVER).addOnSuccessListener { users ->
                if (users.documents.any { it.getString("role") == role && it.getString("profileId") == profileId }) {
                    onResult(Result.failure(IllegalStateException("A login already exists for this profile.")))
                    return@addOnSuccessListener
                }
                val app = FirebaseApp.getInstance()
                val secondary = FirebaseApp.initializeApp(app.applicationContext, app.options, "profile-create-${UUID.randomUUID()}")
                val auth = FirebaseAuth.getInstance(secondary)
                auth.createUserWithEmailAndPassword(login.email, login.password).addOnSuccessListener { credential ->
                    val created = credential.user!!
                    db.runTransaction { tx ->
                        val latest = tx.get(ref)
                        check(latest.exists() && latest.getBoolean("isDeleted") != true && latest.getString("authUid").isNullOrBlank()) { "Profile changed or already has a login." }
                        tx.set(db.collection("users").document(created.uid), mapOf(
                            "role" to role, "schoolId" to schoolId, "profileId" to profileId,
                            "email" to login.email, "name" to latest.getString(if(role == "student") "fullName" else "name").orEmpty(),
                            "active" to (latest.getString("status") in listOf("Active", "On Leave")),
                            "portalDisabled" to false, "createdAt" to FieldValue.serverTimestamp()))
                        tx.update(ref, mapOf("authUid" to created.uid, "portalEmail" to login.email, "updatedAt" to FieldValue.serverTimestamp()))
                        Unit
                    }.addOnSuccessListener {
                        verifyExisting(schoolId, role, profileId, created.uid, login) { verified ->
                            auth.signOut(); secondary.delete(); onResult(verified)
                        }
                    }.addOnFailureListener { error ->
                        created.delete().addOnCompleteListener { cleanup ->
                            auth.signOut(); secondary.delete()
                            onResult(Result.failure(IllegalStateException(if(cleanup.isSuccessful)
                                "Profile saved, but login linking failed. Retry saving to create the login."
                                else "Profile saved, but login linking and account cleanup failed. Contact the administrator.", error)))
                        }
                    }
                }.addOnFailureListener { error ->
                    secondary.delete(); onResult(Result.failure(IllegalStateException(if (error is com.google.firebase.auth.FirebaseAuthUserCollisionException)
                        "This login email is already registered. Check School Short Form and the existing account before retrying."
                        else "Profile saved, but login creation failed. Retry saving. ${error.localizedMessage.orEmpty()}", error)))
                }
            }.addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun verifyExisting(schoolId: String, role: String, profileId: String, uid: String,
                               login: GeneratedLogin, onResult: (Result<Unit>) -> Unit) {
        val app = FirebaseApp.getInstance()
        val secondary = FirebaseApp.initializeApp(app.applicationContext, app.options, "profile-retry-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(secondary)
        fun finish(result: Result<Unit>) { auth.signOut(); secondary.delete(); onResult(result) }
        auth.signInWithEmailAndPassword(login.email, login.password).addOnSuccessListener { signed ->
            if(signed.user?.uid != uid) {
                finish(Result.failure(IllegalStateException("A different account is already linked to this profile.")))
            } else FirebaseFirestore.getInstance(secondary).collection("users").document(uid).get(Source.SERVER).addOnSuccessListener { user ->
                if(user.getString("schoolId") == schoolId && user.getString("role") == role && user.getString("profileId") == profileId)
                    FirebaseFirestore.getInstance(secondary).collection("schools").document(schoolId)
                        .collection(if (role == "teacher") "teachers" else "students").document(profileId)
                        .get(Source.SERVER).addOnSuccessListener { profile ->
                            if (profile.exists() && profile.getString("authUid") == uid)
                                finish(Result.success(Unit))
                            else finish(Result.failure(IllegalStateException("The portal profile link is incomplete.")))
                        }.addOnFailureListener { finish(Result.failure(IllegalStateException("Login was created, but portal access could not be verified. Retry saving.", it))) }
                else finish(Result.failure(IllegalStateException("The existing login link is inconsistent.")))
            }.addOnFailureListener { finish(Result.failure(it)) }
        }.addOnFailureListener { finish(Result.failure(IllegalStateException("A login already exists. The existing password was preserved.", it))) }
    }
}

