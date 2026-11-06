package pk.edunexa.app.operations

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pk.edunexa.app.auth.PortalSession
import java.util.UUID

class SchoolRegistrationRepository(private val actor:PortalSession,private val db:FirebaseFirestore=FirebaseFirestore.getInstance()) {
    suspend fun register(input:SchoolRegistration,password:String):SchoolRegistration {
        val data=SchoolRegistrationPolicy.validate(input,password)
        OwnerRepository(actor,db).authorize()
        // Preflight fails on older rules before any Firebase Auth account is created.
        db.collection("users").document("owner-preflight-${UUID.randomUUID()}").get(Source.SERVER).awaitOperation()
        val root=db.collection("schools").document(data.schoolId)
        val existing=root.get(Source.SERVER).awaitOperation()
        check(!existing.exists() || existing.getString("createdByOwnerUid")==actor.uid) { "School ID is already registered. Choose a different ID." }
        val primary=FirebaseApp.getInstance()
        val app=FirebaseApp.initializeApp(primary.applicationContext,primary.options,"school-register-${UUID.randomUUID()}")
        val auth=FirebaseAuth.getInstance(app)
        try {
            val login=try { auth.createUserWithEmailAndPassword(data.adminEmail,password).awaitOperation() }
                catch(e:FirebaseAuthUserCollisionException) { auth.signInWithEmailAndPassword(data.adminEmail,password).awaitOperation() }
            val uid=login.user?.uid ?: error("Admin account could not be verified.")
            // Auth and Firestore are separate services: retain a verified Auth account on an
            // uncertain failure. Retrying the same credentials safely resumes the same link.
            return withContext(NonCancellable) {
                db.runTransaction {tx->
                    val school=tx.get(root);val accountRef=db.collection("users").document(uid);val account=tx.get(accountRef)
                    val refs=listOf("current","registrationCounter","teacherRegistrationCounter").map{root.collection("academicConfig").document(it)}
                    val configs=refs.map{tx.get(it)}
                    if(school.exists()) {
                        check(school.getString("createdByOwnerUid")==actor.uid && school.getString("adminUid")==uid && account.getString("schoolId")==data.schoolId && account.getString("role")=="admin" && configs.all{it.exists()}) { "School or Admin is already linked differently. No records were changed." }
                        @Suppress("UNCHECKED_CAST") val saved=school.get("settings") as? Map<String,Any?> ?: emptyMap()
                        return@runTransaction data.copy(schoolName=saved["schoolName"]?.toString().orEmpty(),campusName=saved["campusName"]?.toString().orEmpty(),shortName=saved["schoolShortName"]?.toString().orEmpty())
                    }
                    check(!account.exists() && configs.none{it.exists()}) { "Admin or school setup already exists. Existing records were preserved." }
                    tx.set(root,mapOf("name" to data.schoolName,"settings" to mapOf("schoolName" to data.schoolName,"campusName" to data.campusName,"schoolShortName" to data.shortName),"portalAccessEnabled" to true,"createdByOwnerUid" to actor.uid,"adminUid" to uid,"createdAt" to FieldValue.serverTimestamp()))
                    tx.set(accountRef,mapOf("name" to data.adminName,"email" to data.adminEmail,"role" to "admin","schoolId" to data.schoolId,"active" to true,"portalDisabled" to false,"createdByOwnerUid" to actor.uid,"createdAt" to FieldValue.serverTimestamp()))
                    tx.set(refs[0],SchoolRegistrationPolicy.emptyConfig())
                    tx.set(refs[1],mapOf("lastAdmissionNumber" to 0,"updatedAt" to FieldValue.serverTimestamp()))
                    tx.set(refs[2],mapOf("lastEmployeeNumber" to 0,"updatedAt" to FieldValue.serverTimestamp()))
                    data
                }.awaitOperation()
            }
        } finally { auth.signOut();app.delete() }
    }
}
