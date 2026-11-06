package pk.edunexa.app.students

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class RegistrationRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    fun reserveTeacher(schoolId: String, teacherId: String, onResult: (Result<String>) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return onResult(Result.failure(IllegalStateException("Admin login required.")))
        val academic = db.collection("schools").document(schoolId).collection("academicConfig")
        val counter = academic.document("teacherRegistrationCounter")
        val reservation = academic.document("teacher-registration-$teacherId")
        db.runTransaction { tx ->
            val admin = tx.get(db.collection("users").document(uid))
            check(admin.getString("role") == "admin" && admin.getBoolean("active") == true &&
                admin.getBoolean("portalDisabled") != true && admin.getString("schoolId") == schoolId)
            val existing = tx.get(reservation)
            val sequence = tx.get(counter)
            if (existing.exists()) return@runTransaction existing.getString("employeeId")
                ?: error("Employee ID reservation is incomplete.")
            check(sequence.exists()) { "Employee counter needs setup. Refresh and retry." }
            val next = (sequence.getLong("lastEmployeeNumber") ?: 0L) + 1
            val value = EnrollmentPolicy.employee(next)
            tx.update(counter, mapOf("lastEmployeeNumber" to next, "updatedAt" to FieldValue.serverTimestamp()))
            tx.set(reservation, mapOf("teacherId" to teacherId, "employeeId" to value,
                "number" to next, "createdAt" to FieldValue.serverTimestamp()))
            value
        }.addOnSuccessListener { onResult(Result.success(it)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
    fun reserve(schoolId: String, studentId: String, onResult: (Result<String>) -> Unit) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return onResult(Result.failure(IllegalStateException("Admin login required.")))
        val academic=db.collection("schools").document(schoolId).collection("academicConfig")
        val counter=academic.document("registrationCounter")
        val reservation=academic.document("registration-$studentId")
        db.runTransaction { tx ->
            val admin=tx.get(db.collection("users").document(uid))
            check(admin.getString("role")=="admin"&&admin.getBoolean("active")==true&&admin.getBoolean("portalDisabled")!=true&&admin.getString("schoolId")==schoolId)
            val existing=tx.get(reservation);val sequence=tx.get(counter)
            if(existing.exists())return@runTransaction existing.getString("admissionNo") ?: error("Registration reservation is incomplete.")
            check(sequence.exists()) { "Registration counter needs setup. Refresh after the app update." }
            val next=(sequence.getLong("lastAdmissionNumber") ?: 0L)+1
            val value=EnrollmentPolicy.admission(next)
            tx.update(counter,mapOf("lastAdmissionNumber" to next,"updatedAt" to FieldValue.serverTimestamp()))
            tx.set(reservation,mapOf("studentId" to studentId,"admissionNo" to value,"number" to next,"createdAt" to FieldValue.serverTimestamp()))
            value
        }.addOnSuccessListener { onResult(Result.success(it)) }.addOnFailureListener { onResult(Result.failure(it)) }
    }
}
