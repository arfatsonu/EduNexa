package pk.edunexa.app.fees

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class FundSnapshot(val status: String, val balance: Double?)

object FeePolicy {
    const val TUITION = "Monthly Tuition Fee"
    val structureTypes = listOf(TUITION, "Annual Fund", "Other")
    private fun format(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Karachi") }
    fun today() = format("yyyy-MM-dd").format(Date())
    fun month() = format("MMMM yyyy").format(Date())
    fun monthKey(label: String): String { val f = format("MMMM yyyy").apply { isLenient = false }; val date = f.parse(label) ?: error("Invalid month"); require(f.format(date) == label); return format("yyyy-MM").format(date) }
    fun cents(amount: Double): Long { require(amount.isFinite() && amount >= 0 && amount <= 100000000.0) { "Valid amount required." }; return kotlin.math.round(amount * 100).toLong() }
    fun amount(row: Map<String, Any?>, key: String) = (row[key] as? Number)?.toDouble() ?: 0.0
    fun paid(row: Map<String, Any?>) = maxOf(amount(row, "paidAmount"), DiaryPolicy.maps(row["paymentHistory"]).sumOf { amount(it, "amount").coerceAtLeast(0.0) })
    fun balance(row: Map<String, Any?>) = (cents(amount(row, "netPayable")) - cents(paid(row))).coerceAtLeast(0) / 100.0
    fun status(row: Map<String, Any?>): String = when { balance(row) <= 0 -> "Paid"; paid(row) > 0 -> "Partial"; row["dueDate"].toString().matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && row["dueDate"].toString() < today() -> "Overdue"; else -> "Unpaid" }
    fun validatePayment(row: Map<String, Any?>, amount: Double) { require(cents(amount) > 0 && cents(amount) <= cents(balance(row))) { "Payment must be greater than zero and cannot exceed the outstanding balance." } }
    fun normalize(row: Map<String, Any?>) = row + mapOf("paidAmount" to paid(row), "remainingBalance" to balance(row), "status" to status(row))
    fun studentSnapshot(rows: List<DiaryItem>, studentId: String, sessionAliases: Set<String>): Pair<String, Double> {
        require(studentId.isNotBlank() && sessionAliases.any { it.isNotBlank() })
        return snapshot(rows.filter { it.text("studentId") == studentId && it.text("academicSessionId").ifBlank { it.text("academicSession") } in sessionAliases.filter { alias -> alias.isNotBlank() } }.map { it.raw })
    }
    // Display only: missing enrollment sessions never create bills or assume a current session.
    fun displaySnapshot(rows: List<DiaryItem>, studentId: String, sessionAliases: Set<String>): Pair<String, Double?> {
        require(studentId.isNotBlank())
        val own = rows.filter { it.text("studentId") == studentId && (sessionAliases.isEmpty() || it.text("academicSessionId").ifBlank { it.text("academicSession") } in sessionAliases) }
        val tuition = own.filter { it.text("feeType") == TUITION && runCatching { monthKey(it.text("month")) <= monthKey(month()) }.getOrDefault(false) }
        if(sessionAliases.isEmpty()) {
            return "Session not set" to tuition.takeIf { it.isNotEmpty() }?.sumOf { balance(it.raw) }
        }
        val saved = snapshot(own.map { it.raw })
        return saved.first to if(tuition.isEmpty()) null else saved.second
    }
    // Session-wide fund balances come from saved bills, never from the profile's rate.
    fun fundSnapshot(rows: List<DiaryItem>, studentId: String, sessionAliases: Set<String>, type: String): FundSnapshot {
        require(studentId.isNotBlank() && type in setOf("Annual Fund", "Other"))
        val aliases = sessionAliases.filter { it.isNotBlank() }.toSet()
        if(aliases.isEmpty()) return FundSnapshot("Session not set", null)
        val bills = rows.filter { it.text("studentId") == studentId && it.text("feeType") == type &&
            it.text("academicSessionId").ifBlank { it.text("academicSession") } in aliases }
        if(bills.isEmpty()) return FundSnapshot("No saved bill", null)
        val due = bills.sumOf { cents(balance(it.raw)) } / 100.0
        val state = when {
            due <= 0 -> "Paid"
            bills.any { status(it.raw) == "Overdue" } -> "Overdue"
            bills.any { paid(it.raw) > 0 } -> "Partial"
            else -> "Pending"
        }
        return FundSnapshot(state, due)
    }
    fun snapshot(rows: List<Map<String, Any?>>): Pair<String, Double> {
        val current = month(); val key = monthKey(current)
        val tuition = rows.filter { it["feeType"] == TUITION && runCatching { monthKey(it["month"].toString()) <= key }.getOrDefault(false) }
        val due = tuition.sumOf { balance(it) }
        val old = tuition.any { it["month"] != current && balance(it) > 0 }
        val now = tuition.filter { it["month"] == current }
        return (when { old || now.any { status(it) == "Overdue" } -> "Overdue"; now.isEmpty() -> "No fee record"; due <= 0 -> "Paid"; else -> "Pending" }) to due
    }
}

data class FeeData(val config: Map<String, Any?>, val students: List<DiaryItem>, val records: List<DiaryItem>, val schoolName: String) {
    fun options(key: String) = DiaryPolicy.maps(config[key])
    val currentSession get() = pk.edunexa.app.students.EnrollmentPolicy.currentSession(config)
    fun inSession(row: DiaryItem, id: String) = pk.edunexa.app.academic.AcademicSessionPolicy.matches(row.text("academicSessionId").ifBlank { row.text("academicSession") }, listOf(id, options("sessions").firstOrNull { it["id"] == id }?.get("name")?.toString().orEmpty()))
}
class FeeRepository(private val actor: PortalSession, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val root get() = db.collection("schools").document(actor.schoolId)
    private suspend fun authorize() {
        check(actor.role == PortalRole.ADMIN && FirebaseAuth.getInstance().currentUser?.uid == actor.uid) { "Active Admin login required." }
        val user = db.collection("users").document(actor.uid).get(Source.SERVER).ready()
        check(user.getBoolean("active") == true && user.getString("role") == "admin" && user.getString("schoolId") == actor.schoolId)
    }
    suspend fun load(studentId: String? = null): FeeData {
        authorize()
        val configTask = root.collection("academicConfig").document("current").get(Source.SERVER)
        val studentsTask = if(studentId == null) root.collection("students").get(Source.SERVER) else null
        val pupilTask = studentId?.let { root.collection("students").document(it).get(Source.SERVER) }
        val recordsTask = (if(studentId == null) root.collection("fees") else root.collection("fees").whereEqualTo("studentId",studentId)).get(Source.SERVER)
        val schoolTask = root.get(Source.SERVER)
        val config = configTask.ready().data.orEmpty()
        val students = studentsTask?.ready()?.documents?.map { DiaryItem(it.id, it.data.orEmpty()) } ?: listOfNotNull(pupilTask?.ready()?.takeIf { it.exists() }?.let { DiaryItem(it.id,it.data.orEmpty()) })
        val records = recordsTask.ready().documents.map { DiaryItem(it.id, FeePolicy.normalize(it.data.orEmpty())) }
        val settings = schoolTask.ready().get("settings") as? Map<*, *>
        return FeeData(config, students, records, settings?.get("schoolName")?.toString().orEmpty().ifBlank { "EduNexa" })
    }
    // Explicit generation only: historical months never inherit today's profile rates.
    suspend fun syncCurrentMonth(studentIds: List<String>, sessionId: String) {
        authorize(); require(studentIds.isNotEmpty()); val config = root.collection("academicConfig").document("current").get(Source.SERVER).ready().data.orEmpty()
        val session = pk.edunexa.app.students.EnrollmentPolicy.currentSession(config)
        check(session["id"] == sessionId) { "Bills can only be generated for the current session and month." }
        studentIds.distinct().forEach { id ->
            val docs = root.collection("fees").whereEqualTo("studentId", id).get(Source.SERVER).ready().documents
            require(docs.size < 400) { "This student has too many fee records to process safely." }
            val studentRef = root.collection("students").document(id)
            val month = FeePolicy.month(); val monthKey = FeePolicy.monthKey(month); val now = now()
            check(docs.none { it.getString("academicSessionId").isNullOrBlank() && it.getString("feeType") in FeePolicy.structureTypes && (it.getString("feeType") != FeePolicy.TUITION || it.getString("month") == month) }) { "A legacy bill has no academic session. Billing sync was stopped to prevent duplicate charges." }
            val candidates = FeePolicy.structureTypes.associateWith { type -> docs.filter { it.getString("feeType") == type && it.getString("academicSessionId") in setOf(sessionId, session["name"].toString()) && (type != FeePolicy.TUITION || it.getString("month") == month) } }
            require(candidates.values.all { it.size <= 1 }) { "Duplicate fee vouchers were found. Billing was stopped to prevent duplicate charges." }
            val refs = FeePolicy.structureTypes.associateWith { type -> candidates[type]?.firstOrNull()?.reference ?: root.collection("fees").document(("fee-$id-" + when (type) { FeePolicy.TUITION -> "$monthKey-tuition"; "Annual Fund" -> "${session["name"]}-annual-fund"; else -> "${session["name"]}-other-funds" }).replace('/', '_').take(220)) }
            db.runTransaction { tx ->
                val student = tx.get(studentRef)
                val old = docs.associate { it.id to tx.get(it.reference).data.orEmpty() }.toMutableMap()
                val target = refs.mapValues { tx.get(it.value) }
                check(student.exists() && student.getBoolean("isDeleted") != true && student.getString("status") in setOf("Active", "On Leave") && (student.getString("academicSessionId") ?: student.getString("academicSession")) in setOf(sessionId, session["name"].toString())) { "This student is not active in the current session. Refresh and try again." }
                val writes = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>>()
                FeePolicy.structureTypes.forEach { type ->
                    val ref = refs.getValue(type); val before = target.getValue(type); val row = before.data.orEmpty()
                    check(!before.exists() || (before.getString("studentId") == id && before.getString("academicSessionId") in setOf(sessionId, session["name"].toString()))) { "Existing bill scope mismatch." }
                    val base = student.getDouble(when(type) { FeePolicy.TUITION -> "monthlyFee"; "Annual Fund" -> "annualFund"; else -> "otherFunds" }) ?: 0.0
                    FeePolicy.cents(base)
                    val managed = !before.exists() || row["nativeStructureManaged"] == true || row["notes"].toString().contains("Auto-created from student fee structure") || before.id == "fee-$id-$monthKey-tuition"
                    if (managed && (base > 0 || before.exists())) {
                        val percent = if(type == FeePolicy.TUITION) Regex("(\\d+(?:\\.\\d+)?)%").find(student.getString("concession").orEmpty())?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0 else 0.0
                        require(percent in 0.0..100.0)
                        val discount = kotlin.math.round(base * percent) / 100
                        val fine = FeePolicy.amount(row, "fineAmount"); FeePolicy.cents(fine)
                        val net = (FeePolicy.cents(base) - FeePolicy.cents(discount) + FeePolicy.cents(fine)).coerceAtLeast(0) / 100.0
                        val voucher = row["voucherNo"]?.toString()?.takeIf { it.isNotBlank() } ?: "VCH-N-" + java.util.UUID.nameUUIDFromBytes(ref.id.toByteArray()).toString().take(12)
                        val payload = FeePolicy.normalize(row + mapOf("id" to ref.id, "schoolId" to actor.schoolId, "academicSessionId" to sessionId, "studentId" to id, "studentName" to student.getString("fullName"), "rollNo" to student.getString("rollNo"), "admissionNo" to student.getString("admissionNo"), "fatherName" to student.getString("fatherName"), "classId" to student.getString("classId"), "sectionId" to student.getString("sectionId"), "className" to student.getString("className"), "section" to student.getString("section"), "month" to (row["month"] ?: month), "feeType" to type, "dueDate" to (row["dueDate"] ?: "$monthKey-15"), "baseAmount" to base, "discountAmount" to discount, "fineAmount" to fine, "netPayable" to net, "paidAmount" to FeePolicy.paid(row), "paymentHistory" to (row["paymentHistory"] ?: emptyList<Map<String, Any?>>()), "voucherNo" to voucher, "receiptVoucherNo" to (row["receiptVoucherNo"] ?: voucher), "nativeStructureManaged" to true, "notes" to (row["notes"] ?: "Auto-created from student fee structure"), "createdAt" to (row["createdAt"] ?: now), "updatedAt" to now))
                        old[ref.id] = payload; writes.add(ref to payload)
                    }
                }
                writes.forEach { (ref, row) -> tx.set(ref, row, SetOptions.merge()) }
                val relevant = old.values.filter { it["academicSessionId"] in setOf(sessionId, session["name"]) }
                val snapshot = FeePolicy.snapshot(relevant)
                tx.update(studentRef, mapOf("feeStatus" to snapshot.first, "balanceDue" to snapshot.second, "feeSnapshotUpdatedAt" to now))
            }.ready()
        }
    }
    suspend fun receive(record: DiaryItem, amount: Double, method: String, notes: String, paymentId: String): DiaryItem {
        authorize(); require(paymentId.matches(Regex("[a-f0-9-]{36}")) && method.isNotBlank())
        val docsTask = root.collection("fees").whereEqualTo("studentId", record.text("studentId")).get(Source.SERVER)
        val configTask = root.collection("academicConfig").document("current").get(Source.SERVER)
        val docs = docsTask.ready().documents
        require(docs.size < 400)
        val ref = root.collection("fees").document(record.id); val studentRef = root.collection("students").document(record.text("studentId"))
        val config = configTask.ready().data.orEmpty()
        val now = now(); val date = FeePolicy.today(); val receipt = "REC-N-" + paymentId
        return db.runTransaction { tx ->
            val student = tx.get(studentRef); val rows = docs.associate { it.id to tx.get(it.reference).data.orEmpty() }.toMutableMap(); val old = tx.get(ref)
            check(student.exists() && old.exists() && old.getString("studentId") == student.id) { "The student or bill was not found." }
            val history = DiaryPolicy.maps(old.get("paymentHistory"))
            pk.edunexa.app.workflow.WorkflowPolicy.verifyPaymentRetry(history,paymentId,amount,method,notes)
            if (history.any { it["id"] == paymentId }) return@runTransaction DiaryItem(ref.id, FeePolicy.normalize(old.data.orEmpty()))
            check(old.getString("updatedAt").orEmpty() == record.text("updatedAt")) { "The bill has changed. Refresh and check the amount again." }
            FeePolicy.validatePayment(old.data.orEmpty(), amount)
            val payment = mapOf("id" to paymentId, "paymentDate" to date, "amount" to FeePolicy.cents(amount) / 100.0, "paymentMethod" to method, "receiptNo" to receipt, "receiptVoucherNo" to receipt, "receivedBy" to actor.name, "receivedByUid" to actor.uid, "notes" to notes, "createdAt" to now)
            val payload = FeePolicy.normalize(old.data.orEmpty() + mapOf("paidAmount" to (FeePolicy.cents(FeePolicy.paid(old.data.orEmpty())) + FeePolicy.cents(amount)) / 100.0, "paymentHistory" to (history + payment), "paymentDate" to date, "paymentMethod" to method, "receiptNo" to receipt, "receiptVoucherNo" to receipt, "updatedAt" to now))
            rows[ref.id] = payload
            val configSession = student.getString("academicSessionId") ?: student.getString("academicSession")
            val sessionName = DiaryPolicy.maps(config["sessions"]).firstOrNull { it["id"] == configSession || it["name"] == configSession }
            val aliases = setOf(configSession, sessionName?.get("id"), sessionName?.get("name")) - null
            val snapshot = FeePolicy.snapshot(rows.values.filter { it["academicSessionId"] in aliases })
            tx.set(ref, payload + pk.edunexa.app.activity.ActivityHistory.patch(old.data.orEmpty(),payload,actor.uid,actor.name,actor.role.value,"Fee payment received",student.getString("fullName").orEmpty(),now), SetOptions.merge()); tx.update(studentRef, mapOf("feeStatus" to snapshot.first, "balanceDue" to snapshot.second, "lastVoucherNo" to receipt, "lastPaymentDate" to date, "feeSnapshotUpdatedAt" to now))
            DiaryItem(ref.id, payload)
        }.ready()
    }
    private fun now() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
    private suspend fun <T> Task<T>.ready(): T = suspendCoroutine { c -> addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }; addOnCanceledListener { c.resumeWithException(IllegalStateException("Operation cancelled.")) } }
}


