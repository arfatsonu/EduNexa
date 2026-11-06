package pk.edunexa.app.imports

import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.students.EnrollmentPolicy
import java.util.Locale
import java.util.UUID

data class ImportRow(val number: Int, val id: String, val values: Map<String, String>, val errors: List<String>) {
    val name get() = values["FullName"].orEmpty()
}
data class ImportPreview(val role: String, val sessionId: String, val rows: List<ImportRow>)
data class ImportResult(val row: Int, val name: String, val identifier: String = "", val email: String = "", val password: String = "", val error: String = "")

object ImportPolicy {
    const val MAX_ROWS = 100
    val studentColumns = listOf("FullName", "Gender", "Class", "Section", "RollNo", "FatherName", "GuardianPhone", "MonthlyFee", "AnnualFund", "OtherFunds", "Address", "AdmissionDate", "DateOfBirth", "BForm", "FatherCnic", "EmergencyPhone", "FatherOccupation", "Remarks")
    val teacherColumns = listOf("FullName", "Phone", "ContactEmail", "Qualification", "JoiningDate")
    fun columns(role: String) = if (role == "student") studentColumns else teacherColumns
    fun key(value: String) = value.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
    fun phone(value: String) = value.filter { it.isDigit() }.let { if (it.startsWith("92")) "0" + it.drop(2) else it }
    fun prepare(schoolId: String, role: String, sheet: List<List<String>>, config: Map<String, Any?>, existing: List<Map<String, Any?>>): ImportPreview {
        require(role in setOf("student", "teacher"))
        require(sheet.isNotEmpty()) { "The Excel sheet is empty." }
        val headers = sheet.first().map { it.trim() }
        require(headers.distinct().size == headers.size) { "The Excel file has duplicate column headings." }
        val required = if (role == "student") studentColumns.take(8) else teacherColumns.take(2)
        require(required.all { it in headers }) { "Missing headings: " + required.filter { it !in headers }.joinToString() + ". Use the downloaded template." }
        require(headers.filter { it.isNotBlank() }.all { it in columns(role) }) { "Unknown headings. Use the Students or Teachers template without changing its headings." }
        val data = sheet.drop(1).mapIndexedNotNull { index, cells ->
            if (cells.all { it.isBlank() }) null else (index + 2) to headers.mapIndexed { col, header -> header to cells.getOrNull(col).orEmpty().trim() }.toMap()
        }
        require(data.isNotEmpty()) { "Add student or teacher data below the headings first." }
        require(data.size <= MAX_ROWS) { "Import up to $MAX_ROWS people per file. Split this file into smaller files." }
        val session = EnrollmentPolicy.currentSession(config)
        val sessionId = session["id"]?.toString().orEmpty()
        require(sessionId.isNotBlank()) { "Set an active academic session first." }
        val aliases = setOf(sessionId, session["name"]?.toString().orEmpty())
        val seen = mutableSetOf<String>()
        val seenEmails = mutableSetOf<String>()
        val classes = DiaryPolicy.maps(config["classes"]).filter { it["isActive"] == true }
        val sections = DiaryPolicy.maps(config["sections"]).filter { it["isActive"] == true }
        val rows = data.map { (number, input) ->
            val values = input.toMutableMap(); val errors = mutableListOf<String>()
            required.forEach { if (values[it].isNullOrBlank()) errors += "$it is required" }
            if (values["FullName"].orEmpty().length > 120) errors += "FullName is too long"
            val contact = values[if (role == "student") "GuardianPhone" else "Phone"].orEmpty()
            if (contact.filter { it.isDigit() }.length !in 7..15) errors += "Enter a valid phone number (store it as text in Excel)"
            var identity = ""
            if (role == "student") {
                val cls = classes.filter { key(it["name"].toString()) == key(values["Class"].orEmpty()) }.singleOrNull()
                val section = sections.filter { it["classId"] == cls?.get("id") && key(it["name"].toString()) == key(values["Section"].orEmpty()) }.singleOrNull()
                if (cls == null) errors += "Class must match an active school class"
                if (section == null) errors += "Section must match the selected class"
                values["classId"] = cls?.get("id")?.toString().orEmpty(); values["sectionId"] = section?.get("id")?.toString().orEmpty()
                if (values["Gender"] !in listOf("Male", "Female")) errors += "Gender must be Male or Female"
                if (values["RollNo"]?.toLongOrNull()?.let { it > 0 } != true) errors += "RollNo must be a positive whole number"
                listOf("MonthlyFee", "AnnualFund", "OtherFunds").forEach { field ->
                    val text = values[field].orEmpty().replace(",", "").ifBlank { "0" }
                    val amount = text.toDoubleOrNull()
                    if (amount == null || !amount.isFinite() || amount < 0 || amount > 1_000_000 || kotlin.math.abs(amount * 100 - kotlin.math.round(amount * 100)) > 0.00001) errors += "$field must be a non-negative amount with at most 2 decimal places"
                    else values[field] = text
                }
                identity = "$sessionId/${values["classId"]}/${values["sectionId"]}/${EnrollmentPolicy.roll(values["RollNo"])}"
            } else {
                val email = values["ContactEmail"].orEmpty()
                if (email.isNotBlank() && !Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(email)) errors += "ContactEmail is invalid"
                if (email.isNotBlank() && !seenEmails.add(key(email))) errors += "Duplicate teacher contact email in this file"
                identity = phone(contact)
            }
            if (!seen.add(identity)) errors += if (role == "student") "Duplicate roll number in this file" else "Duplicate teacher phone in this file"
            val id = "import-" + UUID.nameUUIDFromBytes("$schoolId/$role/$identity".toByteArray())
            if (existing.any { it["id"] == id && key(it[if (role == "student") "fullName" else "name"]?.toString().orEmpty()) != key(values["FullName"].orEmpty()) }) errors += "This roll/phone belongs to a different imported person"
            if (existing.any { it["id"] == id && it["isDeleted"] == true }) errors += "This imported profile was removed. Add it manually as a new admission."
            val duplicates = existing.filter { it["id"] != id && it["isDeleted"] != true }.any { row ->
                if (role == "student") EnrollmentPolicy.duplicateRoll(row, mapOf("classId" to values["classId"], "sectionId" to values["sectionId"], "rollNo" to values["RollNo"]), aliases)
                else phone(row["phone"]?.toString().orEmpty()) == phone(contact) || (values["ContactEmail"].orEmpty().isNotBlank() && key(row["email"]?.toString().orEmpty()) == key(values["ContactEmail"].orEmpty()))
            }
            if (duplicates) errors += if (role == "student") "Roll number already belongs to a student in this class/session" else "Teacher phone or contact email already exists"
            listOf("AdmissionDate", "DateOfBirth", "JoiningDate").forEach { field ->
                values[field]?.takeIf { it.isNotBlank() }?.let { date ->
                    val valid = runCatching { val format = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US); format.isLenient = false; val parsed = format.parse(date); parsed != null && format.format(parsed) == date }.getOrDefault(false)
                    if (!valid) errors += "$field must use YYYY-MM-DD (text)"
                }
            }
            ImportRow(number, id, values, errors.distinct())
        }
        return ImportPreview(role, sessionId, rows)
    }
}
