package pk.edunexa.app.fees

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import pk.edunexa.app.attendance.CardAttendancePolicy
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.operations.SchoolOperationsRepository
import pk.edunexa.app.operations.awaitOperation

object CardFeePolicy {
    fun validate(schoolId:String,value:String,student:DiaryItem?):DiaryItem {
        val card=CardAttendancePolicy.decode(value)
        require(card.schoolId==schoolId) { "This card belongs to another school." }
        require(student!=null && student.id==card.studentId && student.raw["isDeleted"]!=true) { "This student is no longer available. Use Archived / Old Dues for removed profiles." }
        require(student.text("status") in setOf("Active","On Leave")) { "Student is inactive. Review their saved dues from Fees." }
        return student
    }
}
class CardFeeRepository(private val actor:PortalSession,private val db:FirebaseFirestore=FirebaseFirestore.getInstance()) {
    suspend fun lookup(value:String):DiaryItem {
        SchoolOperationsRepository(actor,db).authorize()
        val card=CardAttendancePolicy.decode(value)
        require(card.schoolId==actor.schoolId) { "This card belongs to another school." }
        val snap=db.collection("schools").document(actor.schoolId).collection("students").document(card.studentId).get(Source.SERVER).awaitOperation()
        return CardFeePolicy.validate(actor.schoolId,value,if(snap.exists())DiaryItem(snap.id,snap.data.orEmpty()) else null)
    }
}
