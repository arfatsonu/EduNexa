package pk.edunexa.app.attendance

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.notifications.NotificationPolicy
import pk.edunexa.app.teachers.TeacherPortalPolicy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CardAttendanceRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    suspend fun scan(value: String): CardAttendanceResult {
        val card=CardAttendancePolicy.decode(value)
        require(card.schoolId == actor.schoolId) { "This card belongs to another school." }
        val root=db.collection("schools").document(actor.schoolId)
        val userRef=db.collection("users").document(actor.uid)
        val user=userRef.get(Source.SERVER).readyCard()
        CardAttendancePolicy.staff(actor,user.data.orEmpty(),FirebaseAuth.getInstance().currentUser?.uid)
        val sourceRef=root.collection("academicConfig").document("current")
        val source=sourceRef.get(Source.SERVER).readyCard()
        val config=source.data.orEmpty()
        val studentRef=root.collection("students").document(card.studentId)
        val pupil=studentRef.get(Source.SERVER).readyCard().let { if(it.exists()) DiaryItem(it.id,it.data.orEmpty()) else null }
        val scope=CardAttendancePolicy.scope(actor,card,pupil,config)
        val date=CardAttendancePolicy.today()
        val cls=scope.student.text("classId");val sec=scope.student.text("sectionId")
        val attendance=root.collection("attendance")
        val found=attendance.whereEqualTo("classId",cls).whereEqualTo("sectionId",sec).whereEqualTo("attendanceDate",date).whereEqualTo("studentId",card.studentId).get(Source.SERVER).readyCard()
        check(found.size() < 50) { "Attendance records need review. Contact the school Admin." }
        check(found.documents.none { it.getString("academicSessionId").orEmpty().ifBlank { it.getString("academicSession").orEmpty() }.isBlank() }) { "Existing attendance has no academic session. Review it before scanning; no changes were made." }
        val canonical=attendance.document(TeacherPortalPolicy.attendanceId(scope.sessionId,date,cls,sec,card.studentId))
        val known=found.documents.filter { it.getString("academicSessionId").orEmpty().ifBlank { it.getString("academicSession").orEmpty() } in setOf(scope.sessionId,scope.sessionName) }.map { it.reference }
        return db.runTransaction { tx ->
            val account=tx.get(userRef)
            CardAttendancePolicy.staff(actor,account.data.orEmpty(),FirebaseAuth.getInstance().currentUser?.uid)
            check(CardAttendancePolicy.today() == date) { "The date changed. Scan the card again." }
            val liveConfig=tx.get(sourceRef).data.orEmpty()
            @Suppress("UNCHECKED_CAST") val settings=tx.get(root).get("settings") as? Map<String,Any?> ?: emptyMap()
            val livePupil=tx.get(studentRef).let { if(it.exists()) DiaryItem(it.id,it.data.orEmpty()) else null }
            val live=CardAttendancePolicy.scope(actor,card,livePupil,liveConfig)
            check(live.sessionId == scope.sessionId && live.student.text("classId") == cls && live.student.text("sectionId") == sec) { "Student class or session changed. Scan again." }
            val saved=(listOf(canonical)+known).distinctBy { it.path }.map { tx.get(it) }.filter { it.exists() }.map { DiaryItem(it.id,it.data.orEmpty()) }
            check(saved.all { it.text("studentId") == card.studentId && it.text("classId") == cls && it.text("sectionId") == sec && it.text("attendanceDate") == date && it.text("academicSessionId").ifBlank { it.text("academicSession") } in setOf(live.sessionId,live.sessionName) }) { "Attendance scope mismatch. Contact Admin." }
            check(saved.all { row -> row.text("status").trim().lowercase() in setOf("","unmarked","pending","present","absent","leave","late") }) { "Existing attendance needs review. No changes were made." }
            val existing=CardAttendancePolicy.existing(saved)
            val name=live.student.text("fullName").ifBlank { live.student.text("name") }
            val admissionNo=live.student.text("admissionNo")
            if(existing != null) return@runTransaction CardAttendanceResult(name,admissionNo,existing.text("status"),true,date)
            val now=NotificationPolicy.now()
            val arrival=ArrivalPolicy.fields(settings)
            check(arrival["arrivalDate"] == date) { "Date changed. Scan again." }
            val payload=mapOf("id" to canonical.id,"schoolId" to actor.schoolId,"academicSessionId" to live.sessionId,"academicSession" to live.sessionName,
                "classId" to cls,"className" to live.className,"sectionId" to sec,"section" to live.sectionName,"studentId" to card.studentId,"studentName" to name,
                "admissionNo" to admissionNo,"rollNo" to live.student.text("rollNo"),"attendanceDate" to date,"date" to date,"status" to "Present","note" to "","remarks" to "",
                "markedBy" to actor.name,"markedByTeacherId" to "","markedAt" to now,"lastUpdatedAt" to now,
                "attendanceMethod" to "QR card","cloudArrivedAt" to FieldValue.serverTimestamp(),"cloudUpdatedAt" to FieldValue.serverTimestamp())
            // Read + write canonical document in one transaction: concurrent scans serialize.
            tx.set(canonical,payload + arrival + pk.edunexa.app.activity.ActivityHistory.patch(saved.firstOrNull { it.id==canonical.id }?.raw.orEmpty(),payload+arrival,actor.uid,actor.name,actor.role.value,"QR attendance saved",name,now))
            CardAttendanceResult(name,admissionNo,arrival["status"].toString(),false,date)
        }.readyCard()
    }
}
private suspend fun <T> com.google.android.gms.tasks.Task<T>.readyCard(): T = suspendCancellableCoroutine { c -> addOnSuccessListener { if(c.isActive) c.resume(it) };addOnFailureListener { if(c.isActive) c.resumeWithException(it) } }
