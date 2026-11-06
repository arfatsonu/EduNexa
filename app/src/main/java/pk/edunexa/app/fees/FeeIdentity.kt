package pk.edunexa.app.fees
import pk.edunexa.app.diary.DiaryItem

/** Presentation only: never changes the saved bill or its financial history. */
object FeeIdentity {
    fun profile(bill: DiaryItem, students: List<DiaryItem>) =
        students.firstOrNull { it.id == bill.text("studentId") && it.id.isNotBlank() }
    fun display(bill: DiaryItem, students: List<DiaryItem>, sessionAliases: List<Set<String>> = emptyList()): DiaryItem {
        val student = profile(bill, students) ?: return bill
        val fields = mutableMapOf<String, Any?>()
        for ((target, source) in listOf("studentName" to "name", "admissionNo" to "admissionNo")) {
            student.text(source).takeIf { it.isNotBlank() }?.let { fields[target] = it }
        }
        val sameClass = student.text("classId").isNotBlank() && student.text("classId") == bill.text("classId") &&
            student.text("sectionId") == bill.text("sectionId")
        val studentSession = student.text("academicSessionId").ifBlank { student.text("academicSession") }
        val billSession = bill.text("academicSessionId").ifBlank { bill.text("academicSession") }
        if (sameClass && studentSession.isNotBlank() && (studentSession == billSession || sessionAliases.any { studentSession in it && billSession in it })) {
            student.text("rollNo").takeIf { it.isNotBlank() }?.let { fields["rollNo"] = it }
        }
        return bill.copy(raw = bill.raw + fields)
    }
}
