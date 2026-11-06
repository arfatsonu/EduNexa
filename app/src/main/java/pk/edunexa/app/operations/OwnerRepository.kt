package pk.edunexa.app.operations

import kotlinx.coroutines.async
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FieldValue
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy

class OwnerRepository(private val actor:PortalSession,private val db:FirebaseFirestore=FirebaseFirestore.getInstance()) {
    internal suspend fun authorize() {
        val auth=FirebaseAuth.getInstance();val user=auth.currentUser ?: error("Sign in again.")
        val token=user.getIdToken(true).awaitOperation()
        val profile=db.collection("users").document(user.uid).get(Source.SERVER).awaitOperation()
        check(OwnerPolicy.allowed(user.uid,actor.uid,token.claims,profile.data.orEmpty())) { "Verified platform Owner permission required. A school Admin cannot enable Owner access." }
    }
    suspend fun load():List<DiaryItem> {
        authorize()
        val schools=db.collection("schools").get(Source.SERVER).awaitOperation().documents
        return kotlinx.coroutines.coroutineScope {
            schools.chunked(10).flatMap { batch -> batch.map { school -> async {
                val setup=school.reference.collection("academicConfig").document("current").get(Source.SERVER).awaitOperation().data.orEmpty()
                DiaryItem(school.id,school.data.orEmpty()+("ownerSetup" to setup))
            } }.map { it.await() } }
        }.sortedBy { (it.raw["settings"] as? Map<*,*>)?.get("schoolName").toString() }
    }
    suspend fun saveProfile(school: DiaryItem, input: Map<String, Any?>) {
        authorize()
        @Suppress("UNCHECKED_CAST") val original = school.raw["settings"] as? Map<String, Any?> ?: emptyMap()
        val patch = OwnerSchoolPolicy.patch(original, input)
        if (patch.isEmpty()) return
        val ref = db.collection("schools").document(school.id)
        db.runTransaction { tx ->
            val live = tx.get(ref); check(live.exists()) { "School no longer exists." }
            @Suppress("UNCHECKED_CAST") val current = live.get("settings") as? Map<String, Any?> ?: emptyMap()
            val merged = OwnerSchoolPolicy.merge(current, original, patch)
            tx.update(ref, mapOf("settings" to merged, "name" to merged["schoolName"], "ownerProfileUpdatedBy" to actor.uid, "ownerProfileUpdatedAt" to FieldValue.serverTimestamp()))
        }.awaitOperation()
    }
    suspend fun admins(schoolId: String): List<DiaryItem> {
        authorize()
        return db.collection("users").whereEqualTo("role", "admin").whereEqualTo("schoolId", schoolId).limit(20).get(Source.SERVER).awaitOperation().documents
            .filter { it.id != actor.uid }.map { DiaryItem(it.id, it.data.orEmpty()) }
    }
    suspend fun saveAdmin(schoolId: String, admin: DiaryItem, name: String, verifiedEmail: String? = null) {
        authorize()
        require(name.trim().length in 2..150) { "Admin name must be 2–150 characters." }
        val email = verifiedEmail?.let(OwnerSchoolPolicy::email)
        val ref = db.collection("users").document(admin.id)
        db.runTransaction { tx ->
            val live = tx.get(ref)
            OwnerSchoolPolicy.admin(live.data.orEmpty(), admin.id, schoolId, actor.uid)
            check(live.getString("name").orEmpty() == admin.text("name") && live.getString("email").orEmpty() == admin.text("email")) { "Admin details changed. Reload this school." }
            val updates = mutableMapOf<String, Any>("name" to name.trim(), "ownerAccountUpdatedBy" to actor.uid, "ownerAccountUpdatedAt" to FieldValue.serverTimestamp())
            if (email != null) updates["email"] = email
            tx.update(ref, updates)
        }.awaitOperation()
    }
    suspend fun access(school:DiaryItem,enabled:Boolean,reason:String) {
        authorize();OwnerPolicy.validateChange(actor.schoolId,school.id,enabled)
        require(reason.trim().length in 3..300) { "Enter a reason (3–300 characters)." }
        val ref=db.collection("schools").document(school.id)
        db.runTransaction { tx ->
            val live=tx.get(ref);check(live.exists()) { "School no longer exists." }
            check((live.getBoolean("portalAccessEnabled")!=false)==(school.raw["portalAccessEnabled"]!=false)) { "School access changed. Refresh first." }
            val event=mapOf("id" to java.util.UUID.randomUUID().toString(),"actorUid" to actor.uid,"actorName" to actor.name,"at" to java.time.Instant.now().toString(),"enabled" to enabled,"reason" to reason.trim())
            tx.update(ref,mapOf("portalAccessEnabled" to enabled,"accessUpdatedBy" to actor.uid,"accessUpdatedAt" to FieldValue.serverTimestamp(),"accessHistory" to (DiaryPolicy.maps(live.get("accessHistory")).takeLast(99)+event)))
        }.awaitOperation()
    }
}
