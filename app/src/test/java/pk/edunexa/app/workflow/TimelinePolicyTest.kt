package pk.edunexa.app.workflow
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
class TimelinePolicyTest {
    private val student=DiaryItem("p",mapOf("admissionDate" to "2026-10-01","admissionNo" to "008"))
    private fun row(id:String,vararg values:Pair<String,Any?>)=DiaryItem(id,mapOf("studentId" to "p")+values)
    @Test fun timelineKeepsRealInstallmentsAndLatestAttendanceWithoutInventingDays(){
        val attendance=listOf(row("old","academicSessionId" to "s","attendanceDate" to "2026-10-02","status" to "Absent","markedAt" to "1"),row("new","academicSessionId" to "s","attendanceDate" to "2026-10-02","status" to "Late","lastUpdatedAt" to "2"),row("pending","attendanceDate" to "2026-10-03","status" to "Unmarked"))
        val fees=listOf(row("fee","paymentHistory" to listOf(mapOf("id" to "1","amount" to 200,"paymentDate" to "2026-10-04"),mapOf("id" to "2","amount" to 300,"paymentDate" to "2026-10-05"))))
        val events=TimelinePolicy.build(student,attendance,fees,emptyList(),emptyList())
        assertEquals(1,events.count{it.type=="Attendance"});assertEquals("Late",events.single{it.type=="Attendance"}.title);assertEquals(2,events.count{it.type=="Fees"});assertEquals("2026-10-05",events.first().date)
    }
    @Test fun otherPupilsUnknownRostersAndUnpublishedResultsAreExcluded(){
        val wrong=row("wrong","studentId" to "other","attendanceDate" to "2026-10-02","status" to "Present")
        val unknown=DiaryItem("legacy",mapOf("date" to "2026-10-02","homework" to "Task"))
        val other=DiaryItem("other",mapOf("date" to "2026-10-02","studentIds" to listOf("other")))
        val draft=row("draft","date" to "2026-10-02","status" to "Present")
        val events=TimelinePolicy.build(student,listOf(wrong),emptyList(),listOf(unknown,other),listOf(draft))
        assertEquals(listOf("Admission"),events.map{it.type})
    }
}
