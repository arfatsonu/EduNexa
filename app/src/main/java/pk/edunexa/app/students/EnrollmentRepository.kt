package pk.edunexa.app.students

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.teachers.StudentRosterUpdatePolicy

/** New admissions update only their class, linked teacher rosters and three fee vouchers. */
class EnrollmentRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    fun save(schoolId: String, studentId: String, payload: Map<String, Any?>, onResult: (Result<Unit>) -> Unit) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return onResult(Result.failure(IllegalStateException("Admin login required.")))
        val root=db.collection("schools").document(schoolId)
        val configRef=root.collection("academicConfig").document("current")
        val configTask=configRef.get(Source.SERVER)
        val admissionTask=root.collection("students").whereEqualTo("admissionNo",payload["admissionNo"]).get(Source.SERVER)
        val classmatesTask=root.collection("students").whereEqualTo("classId",payload["classId"]).whereEqualTo("sectionId",payload["sectionId"]).get(Source.SERVER)
        val inchargesTask=root.collection("teachers").whereEqualTo("inchargeClassId",payload["classId"]).get(Source.SERVER)
        Tasks.whenAllComplete(configTask,admissionTask,classmatesTask,inchargesTask).addOnCompleteListener {
            val failure=listOf(configTask,admissionTask,classmatesTask,inchargesTask).firstOrNull { !it.isSuccessful }?.exception
            if(failure!=null){onResult(Result.failure(failure));return@addOnCompleteListener}
            db.runTransaction { tx ->
                val admin=tx.get(db.collection("users").document(uid))
                check(admin.getString("role")=="admin"&&admin.getBoolean("active")==true&&admin.getBoolean("portalDisabled")!=true&&admin.getString("schoolId")==schoolId) { "Active school Admin required." }
                val configDoc=tx.get(configRef)
                check(configDoc.exists() && configDoc.data == configTask.result.data) { "Academic settings changed. Retry saving." }
                val config=configDoc.data.orEmpty();val session=EnrollmentPolicy.currentSession(config)
                val sessionId=session["id"]?.toString().orEmpty();val aliases=setOf(sessionId,session["name"]?.toString().orEmpty())
                check(sessionId.isNotBlank() && payload["academicSessionId"] in aliases) { "Select the current academic session." }
                check(DiaryPolicy.maps(config["classes"]).any { it["id"]==payload["classId"]&&it["isActive"]==true } &&
                    DiaryPolicy.maps(config["sections"]).any { it["id"]==payload["sectionId"]&&it["classId"]==payload["classId"]&&it["isActive"]==true }) { "Select an active class and section." }
                val studentRef=root.collection("students").document(studentId);val previous=tx.get(studentRef)
                check(previous.getBoolean("isDeleted")!=true) { "This student was removed. Add a new profile." }
                if (studentId.startsWith("import-") && previous.exists()) {
                    check(pk.edunexa.app.imports.ImportPolicy.key(previous.getString("fullName").orEmpty()) == pk.edunexa.app.imports.ImportPolicy.key(payload["fullName"]?.toString().orEmpty())) { "This imported roll number belongs to another student." }
                    return@runTransaction Unit
                }
                val reservation=tx.get(root.collection("academicConfig").document("registration-$studentId"))
                check(reservation.exists()) { "Reserve the automatic registration number before saving." }
                require(payload["admissionNo"]?.toString()?.matches(Regex("[A-Z0-9][A-Z0-9_-]{0,39}")) == true) { "Registration number must contain 1–40 letters, digits, underscores or hyphens." }
                val registrationKey = payload["admissionNo"].toString().trim().lowercase(java.util.Locale.US)
                val registrationLockRef = root.collection("academicConfig").document("admission-" + java.security.MessageDigest.getInstance("SHA-256").digest(registrationKey.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) })
                val registrationLock = tx.get(registrationLockRef)
                check(!registrationLock.exists() || registrationLock.getString("studentId") == studentId) { "This registration number is already assigned." }
                check(!previous.exists() || previous.getString("admissionNo")==payload["admissionNo"]) { "Student identity changed. Reload before saving." }
                val student=previous.data.orEmpty()+payload+mapOf("id" to studentId,"academicSessionId" to sessionId,"academicSession" to sessionId,"academicSessionName" to session["name"],"isDeleted" to false)
                val admissions=admissionTask.result.documents.filter { it.id!=studentId }.map { tx.get(it.reference) }
                check(admissions.none { it.exists() }) { "This registration number was already used. Reopen the student form." }
                val classmates=classmatesTask.result.documents.filter { it.id!=studentId && EnrollmentPolicy.duplicateRoll(it.data.orEmpty(),student,aliases) }.map { tx.get(it.reference) }
                check(classmates.none { it.exists() && EnrollmentPolicy.duplicateRoll(it.data.orEmpty(),student,aliases) }) { "This roll number is already assigned in this class and section." }
                val scope="$sessionId/${payload["classId"]}/${payload["sectionId"]}/${EnrollmentPolicy.roll(payload["rollNo"])}"
                val digest=java.security.MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
                val lockRef=root.collection("academicConfig").document("roll-$digest");val lock=tx.get(lockRef)
                val ownerId=lock.getString("studentId").orEmpty()
                val owner=if(ownerId.isNotBlank()&&ownerId!=studentId)tx.get(root.collection("students").document(ownerId)) else null
                check(owner==null || !owner.exists() || !EnrollmentPolicy.duplicateRoll(owner.data.orEmpty(),student,aliases)) { "This roll number is already assigned in this class and section." }
                val assignments=DiaryPolicy.maps(config["teacherAssignments"])
                val teacherIds=(assignments.filter { it["classId"]==student["classId"]&&it["sectionId"]==student["sectionId"]&&it["academicSession"] in aliases }.map { it["teacherId"].toString() }+
                    inchargesTask.result.documents.filter { it.getString("inchargeSectionId")==student["sectionId"] }.map { it.id }).filter { it.isNotBlank() }.distinct()
                val teachers=teacherIds.map { tx.get(root.collection("teachers").document(it)) }.filter { it.exists()&&it.getBoolean("isDeleted")!=true&&it.getString("status")=="Active" }
                val teacherChanges=teachers.mapNotNull { teacher ->
                    @Suppress("UNCHECKED_CAST") val directory=teacher.get("academicDirectory") as? Map<String,Any?> ?: error("Teacher directory needs refresh before adding students.")
                    check(teacher.getString("activeAcademicSessionId") in aliases) { "Teacher academic session needs refresh." }
                    val assigned=assignments.any { it["teacherId"]==teacher.id&&it["classId"]==student["classId"]&&it["sectionId"]==student["sectionId"]&&it["academicSession"] in aliases }
                    val incharge=teacher.getString("inchargeClassId")==student["classId"]&&teacher.getString("inchargeSectionId")==student["sectionId"]&&teacher.getString("inchargeAcademicSession").orEmpty() in (aliases+"")
                    if(!assigned&&!incharge)return@mapNotNull null
                    val old=DiaryPolicy.maps(directory["studentRoster"]);val next=StudentRosterUpdatePolicy.update(old,student,true)
                    if(old==next)null else teacher.reference to next
                }
                val now=java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",java.util.Locale.US).apply { timeZone=java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
                val bills=EnrollmentPolicy.initialBills(student,schoolId,sessionId,now)
                val savedBills=bills.map { bill -> val ref=root.collection("fees").document(bill["id"].toString());Triple(ref,tx.get(ref),bill) }
                check(savedBills.all { (_,before,_) -> !before.exists() || before.getString("studentId")==studentId && before.getString("academicSessionId") in aliases }) { "Fee voucher scope changed." }
                val summary=FeePolicy.snapshot(savedBills.map { (_,before,bill) -> if(before.exists())before.data.orEmpty() else bill })
                check(teacherChanges.size+savedBills.size+2<500)
                tx.set(studentRef,student + pk.edunexa.app.activity.ActivityHistory.patch(previous.data.orEmpty(),student,uid,admin.getString("name").orEmpty(),"admin","Student admitted",student["fullName"].toString(),now) + mapOf("feeStatus" to summary.first,"balanceDue" to summary.second,"feeSnapshotUpdatedAt" to now),SetOptions.merge())
                teacherChanges.forEach { (ref,roster)->tx.update(ref,mapOf("academicDirectory.studentRoster" to roster,"academicDirectoryUpdatedAt" to FieldValue.serverTimestamp())) }
                savedBills.filter { !it.second.exists() }.forEach { (ref,_,bill)->tx.set(ref,bill) }
                tx.set(lockRef,mapOf("studentId" to studentId,"scope" to scope,"updatedAt" to FieldValue.serverTimestamp()))
                tx.set(registrationLockRef, mapOf("studentId" to studentId, "admissionNo" to payload["admissionNo"], "updatedAt" to FieldValue.serverTimestamp()))
                Unit
            }.addOnSuccessListener { onResult(Result.success(Unit)) }.addOnFailureListener { onResult(Result.failure(it)) }
        }
    }
}
