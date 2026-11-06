package pk.edunexa.app.operations

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.diary.DiaryItem
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitOperation(): T = suspendCancellableCoroutine { c -> addOnSuccessListener { if(c.isActive)c.resume(it) };addOnFailureListener { if(c.isActive)c.resumeWithException(it) };addOnCanceledListener { c.cancel() } }
class SchoolOperationsRepository(private val actor: PortalSession,private val db: FirebaseFirestore=FirebaseFirestore.getInstance()) {
    suspend fun authorize() {
        check(actor.role==PortalRole.ADMIN && FirebaseAuth.getInstance().currentUser?.uid==actor.uid) { "Admin login required." }
        val u=db.collection("users").document(actor.uid).get(Source.SERVER).awaitOperation()
        check(u.getString("role")=="admin" && u.getString("schoolId")==actor.schoolId && u.getBoolean("active")==true && u.getBoolean("portalDisabled")!=true) { "Active school Admin required." }
    }
    suspend fun students(): Pair<List<DiaryItem>,Map<String,Any?>> {
        authorize()
        val root=db.collection("schools").document(actor.schoolId)
        val rows=root.collection("students").get(Source.SERVER).awaitOperation().documents.filter { it.getBoolean("isDeleted")!=true }.map { DiaryItem(it.id,it.data.orEmpty()) }
        return rows to root.collection("academicConfig").document("current").get(Source.SERVER).awaitOperation().data.orEmpty()
    }
    suspend fun history(): List<Map<String,Any?>> {
        authorize()
        val root=db.collection("schools").document(actor.schoolId)
        val records=mutableListOf<DiaryItem>()
        records+=DiaryItem(actor.schoolId,root.get(Source.SERVER).awaitOperation().data.orEmpty())
        // Explicit refresh avoids adding permanent listeners or writes.
        for(collection in listOf("students","teachers","attendance","fees")) {
            records+=root.collection(collection).orderBy("lastEditedAt",com.google.firebase.firestore.Query.Direction.DESCENDING).limit(250).get(Source.SERVER).awaitOperation().documents.map { DiaryItem("$collection/${it.id}",it.data.orEmpty()) }
        }
        return records.flatMap { row -> pk.edunexa.app.diary.DiaryPolicy.maps(row.raw["activityHistory"]).map { it+("source" to row.id) } }.distinctBy { it["id"] }.sortedByDescending { it["at"].toString() }
    }
}
