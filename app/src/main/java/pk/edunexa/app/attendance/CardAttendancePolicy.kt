package pk.edunexa.app.attendance

import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.students.EnrollmentPolicy
import pk.edunexa.app.teachers.TeacherPortalPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class AttendanceCard(val schoolId: String, val studentId: String)
data class ScanScope(val student: DiaryItem, val sessionId: String, val sessionName: String, val className: String, val sectionName: String)
data class CardAttendanceResult(val name: String, val admissionNo: String, val status: String, val alreadyMarked: Boolean, val date: String)

object CardAttendancePolicy {
    private fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_.-]{1,128}")) && id !in setOf(".","..")
    fun encode(schoolId: String, studentId: String): String {
        require(validId(schoolId) && validId(studentId)) { "Invalid school or student card ID." }
        return "EDUNEXA:1:$schoolId:$studentId"
    }
    fun decode(value: String): AttendanceCard {
        val parts=value.trim().split(':')
        require(parts.size == 4 && parts[0] == "EDUNEXA" && parts[1] == "1" && validId(parts[2]) && validId(parts[3])) { "This is not a valid EduNexa student card." }
        return AttendanceCard(parts[2],parts[3])
    }
    fun today(now: Date = Date()) = SimpleDateFormat("yyyy-MM-dd",Locale.US).apply { timeZone=TimeZone.getTimeZone("Asia/Karachi") }.format(now)
    fun staff(actor: PortalSession, user: Map<String,Any?>, uid: String?) {
        check(uid == actor.uid && actor.role == PortalRole.ADMIN && user["role"] == actor.role.value && user["active"] == true && user["portalDisabled"] != true && user["schoolId"] == actor.schoolId) { "Use the school Admin login for the card attendance scanner." }
    }
    fun scope(actor: PortalSession, card: AttendanceCard, student: DiaryItem?, config: Map<String,Any?>): ScanScope {
        require(card.schoolId == actor.schoolId) { "This card belongs to another school." }
        check(actor.role == PortalRole.ADMIN) { "Only the school Admin can run the card attendance scanner." }
        // Match dashboard/admissions: explicit current first, otherwise first Active session.
        val current=EnrollmentPolicy.currentSession(config)
        val sessionStatus=current["status"]?.toString().orEmpty().trim()
        val legacyCurrent=current["isCurrent"] == true && sessionStatus.isBlank()
        check(current.isNotEmpty() && (sessionStatus.equals("Active",true) || legacyCurrent)) { "An active academic session is required. Select an Active session in Classes & Sessions." }
        val sessionId=current["id"]?.toString().orEmpty();val sessionName=current["name"]?.toString().orEmpty()
        check(sessionId.isNotBlank()) { "Current session is missing." }
        val pupil=student ?: error("Student card was not found in your authorized class.")
        check(pupil.id == card.studentId && pupil.text("schoolId") in setOf("",actor.schoolId) && pupil.raw["isDeleted"] != true && pupil.text("status") in setOf("Active","On Leave")) { "This student is inactive or the card is invalid." }
        val savedSession=pupil.text("academicSessionId").ifBlank { pupil.text("academicSession") }
        check(savedSession.isNotBlank() && savedSession in setOf(sessionId,sessionName)) { "This student is not enrolled in the current session." }
        val cls=pupil.text("classId");val sec=pupil.text("sectionId")
        val className=DiaryPolicy.maps(config["classes"]).firstOrNull { it["id"] == cls && it["isActive"] == true }?.get("name")?.toString() ?: error("Student class is inactive.")
        val sectionName=DiaryPolicy.maps(config["sections"]).firstOrNull { it["id"] == sec && it["classId"] == cls && it["isActive"] == true }?.get("name")?.toString() ?: error("Student section is inactive.")
        return ScanScope(pupil,sessionId,sessionName,className,sectionName)
    }
    fun existing(rows: List<DiaryItem>): DiaryItem? = rows.map { row ->
        val normalized=TeacherPortalPolicy.attendanceStatuses.firstOrNull { it.equals(row.text("status").trim(),true) }
        if(normalized == null) row else DiaryItem(row.id,row.raw+("status" to normalized))
    }.filter { it.text("status") in TeacherPortalPolicy.attendanceStatuses }
        .maxWithOrNull(compareBy({ TeacherPortalPolicy.revision(it) },{it.id}))
}
