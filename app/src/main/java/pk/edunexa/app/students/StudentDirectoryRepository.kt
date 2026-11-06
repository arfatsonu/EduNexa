package pk.edunexa.app.students

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class StudentDirectoryItem(
    val id: String,
    val fullName: String,
    val admissionNo: String,
    val rollNo: String,
    val admissionDate: String,
    val gender: String,
    val dateOfBirth: String,
    val bloodGroup: String,
    val bForm: String,
    val className: String,
    val section: String,
    val classId: String,
    val sectionId: String,
    val fatherName: String,
    val fatherOccupation: String,
    val fatherCnic: String,
    val guardianPhone: String,
    val emergencyPhone: String,
    val address: String,
    val status: String,
    val feeStatus: String,
    val monthlyFee: Double,
    val annualFund: Double,
    val otherFunds: Double,
    val balanceDue: Double,
    val portalEmail: String,
    val remarks: String,
    val isNewAdmission: Boolean,
    val profilePhoto: String?,
    val academicSessionId: String = "",
    val feeBalanceAvailable: Boolean = true,
    val annualFundSnapshot: pk.edunexa.app.fees.FundSnapshot = pk.edunexa.app.fees.FundSnapshot("Loading…", null),
    val otherFundsSnapshot: pk.edunexa.app.fees.FundSnapshot = pk.edunexa.app.fees.FundSnapshot("Loading…", null)
)

data class StudentSectionOption(
    val id: String,
    val name: String,
    val displayOrder: Int
)

data class StudentClassOption(
    val id: String,
    val name: String,
    val displayOrder: Int,
    val sections: List<StudentSectionOption>
)

class StudentDirectoryRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1")
) {
    fun observeStudents(
        schoolId: String,
        onResult: (Result<List<StudentDirectoryItem>>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("schools").document(schoolId).collection("students").addSnapshotListener { snapshot, error ->
            if(error != null || snapshot == null) {
                onResult(Result.failure(IllegalStateException("Could not load students. Check your connection and permissions.", error)))
                return@addSnapshotListener
            }
                val items = snapshot.documents.filter { it.getBoolean("isDeleted") != true }.map { doc ->
                    fun number(field: String): Double =
                        (doc.get(field) as? Number)?.toDouble() ?: 0.0

                    StudentDirectoryItem(
                        id = doc.id,
                        fullName = doc.getString("fullName").orEmpty(),
                        admissionNo = doc.getString("admissionNo").orEmpty(),
                        rollNo = doc.getString("rollNo").orEmpty(),
                        admissionDate = doc.getString("admissionDate").orEmpty(),
                        gender = doc.getString("gender").orEmpty(),
                        dateOfBirth = doc.getString("dateOfBirth").orEmpty(),
                        bloodGroup = doc.getString("bloodGroup").orEmpty(),
                        bForm = doc.getString("bForm").orEmpty(),
                        className = doc.getString("className").orEmpty(),
                        section = doc.getString("section").orEmpty(),
                        classId = doc.getString("classId").orEmpty(),
                        sectionId = doc.getString("sectionId").orEmpty(),
                        fatherName = doc.getString("fatherName").orEmpty(),
                        fatherOccupation = doc.getString("fatherOccupation").orEmpty(),
                        fatherCnic = doc.getString("fatherCnic").orEmpty(),
                        guardianPhone = doc.getString("guardianPhone").orEmpty(),
                        emergencyPhone = doc.getString("emergencyPhone").orEmpty(),
                        address = doc.getString("address").orEmpty(),
                        status = doc.getString("status").orEmpty(),
                        academicSessionId = doc.getString("academicSessionId").orEmpty().ifBlank { doc.getString("academicSession").orEmpty() },
                        feeStatus = doc.getString("feeStatus").orEmpty(),
                        monthlyFee = number("monthlyFee"),
                        annualFund = number("annualFund"),
                        otherFunds = number("otherFunds"),
                        balanceDue = number("balanceDue"),
                        portalEmail = doc.getString("portalEmail").orEmpty(),
                        remarks = doc.getString("remarks").orEmpty(),
                        isNewAdmission = doc.getBoolean("isNewAdmission") ?: false,
                        profilePhoto = doc.getString("profilePhoto")
                    )
                }.sortedWith(
                    compareBy<StudentDirectoryItem>(
                        { Regex("\\d+").find(it.className)?.value?.toIntOrNull() ?: Int.MAX_VALUE },
                        { it.section.lowercase(Locale.getDefault()) },
                        { it.rollNo.toIntOrNull() ?: Int.MAX_VALUE },
                        { it.fullName.lowercase(Locale.getDefault()) }
                    )
                )
                onResult(Result.success(items))
        }
    }

    fun sendGuardianMessage(
        schoolId: String,
        student: StudentDirectoryItem,
        senderName: String,
        message: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val clean = message.trim()
        if (clean.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("The message is empty.")))
            return
        }

        val id = "notice-${System.currentTimeMillis()}-${student.id}"
        val now = isoNow()
        val data = hashMapOf<String, Any>(
            "id" to id,
            "schoolId" to schoolId,
            "title" to "Parent / Guardian Message — ${student.fullName}",
            "message" to clean,
            "priority" to "Normal",
            "audience" to "Individual",
            "recipientId" to student.id,
            "recipientName" to student.fullName,
            "recipientIds" to listOf(student.id),
            "status" to "Published",
            "publishAt" to now,
            "createdBy" to senderName,
            "createdAt" to now,
            "updatedAt" to now,
            "cloudUpdatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("schools").document(schoolId)
            .collection("notifications").document(id).set(data)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { error ->
                onResult(
                    Result.failure(
                        IllegalStateException("Could not send the in-app message.", error)
                    )
                )
            }
    }

    private fun isoNow(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
    fun newStudentId(schoolId: String): String =
        db.collection("schools").document(schoolId).collection("students").document().id

    fun loadLoginShortName(schoolId: String, onResult: (Result<String>) -> Unit) {
        db.collection("schools").document(schoolId).get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { doc -> onResult(Result.success(doc.getString("settings.schoolShortName").orEmpty())) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun loadAcademicOptions(
        schoolId: String,
        onResult: (Result<List<StudentClassOption>>) -> Unit
    ) {
        db.collection("schools").document(schoolId)
            .collection("academicConfig").document("current").get()
            .addOnSuccessListener { doc ->
                try {
                    val rawClasses = doc.get("classes") as? List<*> ?: emptyList<Any>()
                    val rawSections = doc.get("sections") as? List<*> ?: emptyList<Any>()

                    fun mapOf(item: Any?): Map<*, *>? = item as? Map<*, *>
                    fun str(map: Map<*, *>, key: String): String =
                        map[key]?.toString()?.trim().orEmpty()
                    fun active(map: Map<*, *>): Boolean =
                        map["isActive"] as? Boolean ?: false
                    fun order(map: Map<*, *>): Int =
                        (map["displayOrder"] as? Number)?.toInt() ?: Int.MAX_VALUE

                    val sectionMaps = rawSections.mapNotNull(::mapOf)
                    val options = rawClasses.mapNotNull(::mapOf)
                        .filter(::active)
                        .map { cls ->
                            val classId = str(cls, "id")
                            StudentClassOption(
                                id = classId,
                                name = str(cls, "name"),
                                displayOrder = order(cls),
                                sections = sectionMaps
                                    .filter { active(it) && str(it, "classId") == classId }
                                    .map {
                                        StudentSectionOption(
                                            id = str(it, "id"),
                                            name = str(it, "name"),
                                            displayOrder = order(it)
                                        )
                                    }
                                    .sortedBy { it.displayOrder }
                            )
                        }
                        .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                        .sortedBy { it.displayOrder }

                    onResult(Result.success(options))
                } catch (error: Exception) {
                    onResult(Result.failure(IllegalStateException("Could not load academic classes and sections.", error)))
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(IllegalStateException("Could not load academic settings.", error)))
            }
    }

    fun updateStudentCredentials(
        schoolId: String,
        studentId: String,
        email: String,
        newPassword: String,
        onResult: (Result<String>) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!cleanEmail.contains("@")) {
            onResult(Result.failure(IllegalArgumentException("Enter a valid email address.")))
            return
        }
        if (newPassword.isNotBlank() && newPassword.length < 6) {
            onResult(Result.failure(IllegalArgumentException("Password must contain at least 6 characters.")))
            return
        }

        val payload = hashMapOf<String, Any>(
            "schoolId" to schoolId,
            "studentId" to studentId,
            "email" to cleanEmail,
            "password" to newPassword
        )

        functions.getHttpsCallable("updateStudentCredentials")
            .call(payload)
            .addOnSuccessListener { result ->
                val data = result.data as? Map<*, *>
                val updatedEmail = data?.get("email")?.toString()?.trim().orEmpty()
                onResult(Result.success(updatedEmail.ifBlank { cleanEmail }))
            }
            .addOnFailureListener { error ->
                val message = when {
                    error.message.orEmpty().contains("NOT_FOUND", true) ->
                        "The credential service is not available yet."
                    error.message.orEmpty().contains("already", true) ->
                        "This email address is already used by another account."
                    else -> error.message ?: "Could not update login credentials."
                }
                onResult(Result.failure(IllegalStateException(message, error)))
            }
    }

    fun saveStudent(
        schoolId: String,
        activeSessionId: String,
        student: StudentDirectoryItem,
        original: StudentDirectoryItem? = null,
        onResult: (Result<Unit>) -> Unit
    ) {
        val studentsRef = db.collection("schools").document(schoolId).collection("students")
        val needsDuplicateCheck = original == null ||
            !original.admissionNo.trim().equals(student.admissionNo.trim(), true) ||
            original.classId != student.classId || original.sectionId != student.sectionId ||
            !original.rollNo.trim().equals(student.rollNo.trim(), true)
        val duplicateTask = if(needsDuplicateCheck && original != null) studentsRef.get() else
            com.google.android.gms.tasks.Tasks.forResult<com.google.firebase.firestore.QuerySnapshot?>(null)
        duplicateTask.addOnSuccessListener { snapshot ->
                val admissionChanged = original == null ||
                    !original.admissionNo.trim()
                        .equals(student.admissionNo.trim(), ignoreCase = true)

                if (admissionChanged) {
                    val duplicateAdmission = snapshot?.documents.orEmpty().any {
                        it.id != student.id &&
                            it.getString("admissionNo").orEmpty().trim()
                                .equals(student.admissionNo.trim(), ignoreCase = true)
                    }
                    if (duplicateAdmission) {
                        onResult(Result.failure(IllegalArgumentException("This admission number is already assigned to another student.")))
                        return@addOnSuccessListener
                    }
                }

                val rollScopeChanged = original == null ||
                    original.classId != student.classId ||
                    original.sectionId != student.sectionId ||
                    !original.rollNo.trim().equals(student.rollNo.trim(), ignoreCase = true)

                if (rollScopeChanged) {
                    val duplicateRoll = snapshot?.documents.orEmpty().any {
                        it.id != student.id && it.getBoolean("isDeleted") != true && it.getString("status") != "Inactive" &&
                            (it.getString("academicSessionId").orEmpty().ifBlank { it.getString("academicSession").orEmpty() } == activeSessionId) &&
                            it.getString("classId").orEmpty() == student.classId &&
                            it.getString("sectionId").orEmpty() == student.sectionId &&
                            it.getString("rollNo").orEmpty().trim()
                                .equals(student.rollNo.trim(), ignoreCase = true)
                    }
                    if (duplicateRoll) {
                        onResult(Result.failure(IllegalArgumentException("This roll number is already assigned in this class and section.")))
                        return@addOnSuccessListener
                    }
                }

                val payload = hashMapOf<String, Any?>(
                    "id" to student.id,
                    "fullName" to student.fullName.trim(),
                    "admissionNo" to student.admissionNo.trim(),
                    "rollNo" to student.rollNo.trim(),
                    "admissionDate" to student.admissionDate.trim(),
                    "gender" to student.gender,
                    "dateOfBirth" to student.dateOfBirth.trim(),
                    "bloodGroup" to student.bloodGroup.trim(),
                    "bForm" to student.bForm.trim(),
                    "className" to student.className,
                    "section" to student.section,
                    "classId" to student.classId,
                    "sectionId" to student.sectionId,
                    "fatherName" to student.fatherName.trim(),
                    "fatherOccupation" to student.fatherOccupation.trim(),
                    "fatherCnic" to student.fatherCnic.trim(),
                    "guardianPhone" to student.guardianPhone.trim(),
                    "emergencyPhone" to student.emergencyPhone.trim(),
                    "address" to student.address.trim(),
                    "status" to student.status,
                    "monthlyFee" to student.monthlyFee,
                    "annualFund" to student.annualFund,
                    "otherFunds" to student.otherFunds,
                    "portalEmail" to student.portalEmail,
                    "remarks" to student.remarks.trim(),
                    "isNewAdmission" to student.isNewAdmission,
                    "academicSession" to activeSessionId,
                    "academicSessionId" to activeSessionId,
                    "profilePhoto" to student.profilePhoto,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if(original == null) EnrollmentRepository(db).save(schoolId, student.id, payload, onResult)
                else pk.edunexa.app.teachers.TeacherDirectoryRepository(db).saveStudentWithDirectory(schoolId, student.id, payload, onResult)

            }
            .addOnFailureListener { error ->
                onResult(Result.failure(IllegalStateException("Could not load students to check for duplicates.", error)))
            }
    }

    fun deleteStudentSafely(
        schoolId: String,
        studentId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val school = db.collection("schools").document(schoolId)
        val ref = school.collection("students").document(studentId)
        db.collection("users").whereEqualTo("schoolId", schoolId).whereEqualTo("profileId", studentId).get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { accounts ->
                db.runTransaction { tx ->
                    val caller=com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in again")
                    val admin=tx.get(db.collection("users").document(caller))
                    check(admin.getString("role")=="admin" && admin.getBoolean("active")==true && admin.getBoolean("portalDisabled")!=true && admin.getString("schoolId")==schoolId) { "Active school Admin required." }
                    val student = tx.get(ref)
                    check(student.exists()) { "The student record no longer exists." }
                    val uid = student.getString("authUid").orEmpty()
                    val refs = (accounts.documents.filter { it.getString("role") == "student" && it.getString("profileId") == studentId }.map { it.reference } +
                        if(uid.isBlank()) emptyList() else listOf(db.collection("users").document(uid))).distinctBy { it.path }
                    val linked = refs.map { tx.get(it) }
                    linked.filter { it.exists() }.forEach {
                        check(it.getString("schoolId") == schoolId && it.getString("role") == "student" && it.getString("profileId") == studentId) { "Login linkage is inconsistent. Deletion was stopped." }
                    }
                    linked.filter { it.exists() }.forEach { tx.update(it.reference, mapOf("active" to false, "portalDisabled" to true, "updatedAt" to FieldValue.serverTimestamp())) }
                    tx.update(ref, mapOf("isDeleted" to true, "status" to "Inactive", "deletedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()) + pk.edunexa.app.activity.ActivityHistory.patch(student.data.orEmpty(),mapOf("isDeleted" to true,"status" to "Inactive"),caller,admin.getString("name").orEmpty(),"admin","Student removed",student.getString("fullName").orEmpty()))
                    Unit
                }.addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(IllegalStateException("Could not remove the student and disable portal access.", it))) }
            }.addOnFailureListener { onResult(Result.failure(it)) }
    }

}



