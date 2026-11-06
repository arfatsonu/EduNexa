package pk.edunexa.app.attendance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.teachers.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TeacherAttendanceData(val access: TeacherAccess, val date: String, val records: List<DiaryItem>) {
    val students get() = if(access.incharge) TeacherPortalPolicy.roster(access, access.inchargeClass, access.inchargeSection) else emptyList()
    val entries get() = TeacherPortalPolicy.latest(records).filterKeys { id -> students.any { it.id == id } }
}
class TeacherAttendanceRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val root get() = db.collection("schools").document(actor.schoolId)
    suspend fun load(date: String): TeacherAttendanceData {
        val access = TeacherAccessRepository(actor, db).load()
        if(!access.incharge) return TeacherAttendanceData(access, date, emptyList())
        val records = root.collection("attendance").whereEqualTo("classId", access.inchargeClass).whereEqualTo("sectionId", access.inchargeSection)
            .whereEqualTo("attendanceDate", date).get(Source.SERVER).teacherReady().documents
            .filter { it.getString("academicSessionId").orEmpty().ifBlank { it.getString("academicSession").orEmpty() } in setOf(access.sessionId,access.sessionName) }
            .map { DiaryItem(it.id,it.data.orEmpty()) }
        return TeacherAttendanceData(access,date,records)
    }
    suspend fun save(before: TeacherAttendanceData, statuses: Map<String,String>, remarks: Map<String,String>, followUps: Map<String,String>) = withContext(Dispatchers.IO) {
        val fresh = TeacherAccessRepository(actor,db).load()
        TeacherPortalPolicy.validateAttendance(fresh,before.date,FeePolicy.today(),statuses)
        val students = TeacherPortalPolicy.roster(fresh,fresh.inchargeClass,fresh.inchargeSection)
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        
        val batch = db.batch()
        students.forEach { student ->
            val ref = root.collection("attendance").document(before.entries[student.id]?.id ?: TeacherPortalPolicy.attendanceId(fresh.sessionId,before.date,fresh.inchargeClass,fresh.inchargeSection,student.id))
            val status = statuses[student.id].orEmpty().ifBlank { "Present" }
            val payload = mutableMapOf<String,Any?>(
                "id" to ref.id, "schoolId" to actor.schoolId,
                "academicSessionId" to fresh.sessionId, "academicSession" to fresh.sessionName,
                "attendanceDate" to before.date, "date" to before.date,
                "classId" to fresh.inchargeClass, "className" to fresh.name("classes",fresh.inchargeClass),
                "sectionId" to fresh.inchargeSection, "section" to fresh.name("sections",fresh.inchargeSection),
                "studentId" to student.id, "studentName" to student.text("fullName"),
                "admissionNo" to student.text("admissionNo"), "rollNo" to student.text("rollNo"),
                "status" to status, "remarks" to remarks[student.id].orEmpty().trim(),
                "note" to remarks[student.id].orEmpty().trim(), "markedBy" to actor.name,
                "markedByTeacherId" to actor.profileId, "markedAt" to now, "lastUpdatedAt" to now,
                "cloudUpdatedAt" to FieldValue.serverTimestamp()
            )
            val follow = followUps[student.id].orEmpty().trim()
            if(status == "Absent" && follow.isNotBlank()) {
                payload["followUpComment"] = follow; payload["followUpUpdatedBy"] = actor.name; payload["followUpUpdatedAt"] = now
            }
            batch.set(ref, payload, SetOptions.merge())
        }
        batch.commit().await()
    }
}
