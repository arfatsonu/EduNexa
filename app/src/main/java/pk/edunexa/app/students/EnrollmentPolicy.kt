package pk.edunexa.app.students

import pk.edunexa.app.academic.AcademicSessionPolicy
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.fees.FeePolicy

object EnrollmentPolicy {
    fun currentSession(config: Map<String, Any?>) = AcademicSessionPolicy.current(config)
    fun roll(value: Any?) = value?.toString().orEmpty().trim().let { it.toLongOrNull()?.toString() ?: it.lowercase() }
    fun duplicateRoll(existing: Map<String, Any?>, next: Map<String, Any?>, aliases: Collection<String>) =
        existing["isDeleted"] != true && existing["status"] != "Inactive" &&
        existing["classId"] == next["classId"] && existing["sectionId"] == next["sectionId"] &&
        AcademicSessionPolicy.matches(existing["academicSessionId"]?.toString() ?: existing["academicSession"]?.toString(),aliases,allowMissing=true) &&
        roll(existing["rollNo"]).isNotBlank() && roll(existing["rollNo"]) == roll(next["rollNo"])
    fun admission(number: Long): String { require(number > 0); return "STU-" + number.toString().padStart(3,'0') }
    fun employee(number: Long): String { require(number > 0); return "T-" + number.toString().padStart(3,'0') }
    fun initialBills(student: Map<String, Any?>, schoolId: String, sessionId: String, now: String): List<Map<String, Any?>> {
        if(student["status"] !in setOf("Active","On Leave"))return emptyList()
        val id=student["id"].toString();require(id.isNotBlank() && sessionId.isNotBlank())
        val month=FeePolicy.month();val monthKey=FeePolicy.monthKey(month)
        val concession=Regex("(\\d+(?:\\.\\d+)?)%").find(student["concession"]?.toString().orEmpty())?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        require(concession in 0.0..100.0)
        return FeePolicy.structureTypes.mapNotNull { type ->
            val base=FeePolicy.amount(student,when(type){FeePolicy.TUITION->"monthlyFee";"Annual Fund"->"annualFund";else->"otherFunds"});FeePolicy.cents(base)
            if(base<=0)return@mapNotNull null
            val discount=if(type==FeePolicy.TUITION) kotlin.math.round(base*concession)/100 else 0.0
            val billId="fee-$id-"+when(type){FeePolicy.TUITION->"$monthKey-tuition";"Annual Fund"->"$sessionId-annual-fund";else->"$sessionId-other-funds"}
            FeePolicy.normalize(student.filterKeys { it in setOf("studentId","classId","className","sectionId","section","rollNo","admissionNo","fatherName") } + mapOf(
                "id" to billId,"studentId" to id,"schoolId" to schoolId,"studentName" to student["fullName"],"academicSessionId" to sessionId,
                "feeType" to type,"month" to month,"dueDate" to "$monthKey-15","baseAmount" to base,"discountAmount" to discount,
                "fineAmount" to 0.0,"netPayable" to (FeePolicy.cents(base)-FeePolicy.cents(discount))/100.0,"paidAmount" to 0.0,
                "paymentHistory" to emptyList<Map<String,Any?>>(),"voucherNo" to "VCH-N-" + java.util.UUID.nameUUIDFromBytes(billId.toByteArray()).toString().take(12),"receiptVoucherNo" to "VCH-N-" + java.util.UUID.nameUUIDFromBytes(billId.toByteArray()).toString().take(12),
                "nativeStructureManaged" to true,"createdAt" to now,"updatedAt" to now,"notes" to "Auto-created from student fee structure"))
        }
    }
}
