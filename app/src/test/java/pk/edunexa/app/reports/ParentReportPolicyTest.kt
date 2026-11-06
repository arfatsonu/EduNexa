package pk.edunexa.app.reports

import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem

class ParentReportPolicyTest {
    private val student=DiaryItem("a",mapOf("fullName" to "Student", "classId" to "current"))
    private val config=mapOf<String,Any?>("sessions" to listOf(mapOf("id" to "s","name" to "2026–2027")))
    private fun row(id: String, fields: Map<String,Any?>)=DiaryItem(id,mapOf("academicSessionId" to "s", "date" to "2026-10-01", "studentIds" to listOf("a"))+fields)
    private fun data(attendance: List<DiaryItem> = emptyList(), diaries: List<DiaryItem> = emptyList(), checks: List<DiaryItem> = emptyList(), tests: List<DiaryItem> = emptyList(), marks: List<DiaryItem> = emptyList())=ParentReportData(student,config,emptyMap(),attendance,diaries,checks,tests,marks)
    @Test fun attendanceDeduplicatesDaysAndDoesNotInventAbsentDays() {
        val saved=listOf(row("old",mapOf("studentId" to "a","status" to "Absent","markedAt" to "2026-10-01T08:00")),row("new",mapOf("studentId" to "a","status" to "Late","lastUpdatedAt" to "2026-10-01T09:00")),row("other",mapOf("studentId" to "b","status" to "Absent")),row("oldSession",mapOf("studentId" to "a","academicSessionId" to "old","status" to "Absent")))
        val section=ParentReportPolicy.build(data(attendance=saved),"s","October 2026").sections[0]
        assertEquals(1,section.rows.size);assertEquals("Late",section.rows.single()[2]);assertEquals("100%",section.metrics.last().second)
        assertEquals("—",ParentReportPolicy.build(data(),"s","October 2026").sections[0].metrics.last().second)
    }
    @Test fun homeworkCountsDaysSeparatelyAndUncheckedIsNeverNotDone() {
        val diaries=listOf(row("one",mapOf("homework" to "Math","subject" to "Math")),row("two",mapOf("homework" to "English","subject" to "English")),row("three",mapOf("date" to "2026-10-02","homework" to "Math")))
        val checks=listOf(row("check",mapOf("diaryId" to "one","records" to listOf(mapOf("studentId" to "a","status" to "Completed")))))
        val section=ParentReportPolicy.build(data(diaries=diaries,checks=checks),"s","October 2026").sections[1]
        assertEquals("2",section.metrics.toMap()["Homework Days"])
        assertEquals("0",section.metrics.toMap()["Not Done Days"])
        assertEquals("2",section.metrics.toMap()["Unchecked Days"])
        assertEquals("1 / 3",section.metrics.toMap()["Assignments Completed"])
    }
    @Test fun knownOtherStudentRosterAndUnlinkedLegacyHomeworkAreExcluded() {
        val diaries=listOf(row("other",mapOf("homework" to "Task","studentIds" to listOf("b"))),row("legacy",mapOf("homework" to "Task","studentIds" to null)))
        assertTrue(ParentReportPolicy.build(data(diaries=diaries),"s","October 2026").sections[1].rows.isEmpty())
    }
    @Test fun testsKeepTypesSubjectsAbsencesAndMissingMarksSeparate() {
        val tests=listOf(row("math",mapOf("assessmentType" to "Weekly Test","subject" to "Math","totalMarks" to 20)),row("english",mapOf("assessmentType" to "Weekly Test","subject" to "English","totalMarks" to 20)),row("pending",mapOf("assessmentType" to "Monthly Test","totalMarks" to 20)),row("other",mapOf("assessmentType" to "Class Test","studentIds" to listOf("b"))))
        val marks=listOf(row("m",mapOf("testId" to "math","studentId" to "a","status" to "Present","marksObtained" to 10)),row("e",mapOf("testId" to "english","studentId" to "a","status" to "Absent")))
        val sections=ParentReportPolicy.build(data(tests=tests,marks=marks),"s","October 2026").sections.drop(2)
        assertEquals(2,sections.size)
        val weekly=sections.first { it.title=="Weekly Test" }
        assertEquals("English",weekly.rows[0][0]);assertEquals("—",weekly.rows[0][4]);assertEquals("50%",weekly.rows[1][6])
        assertEquals("Not Recorded",sections.first { it.title=="Monthly Test" }.rows.single()[3])
    }
    @Test fun wholeSessionTestsIncludePriorMonthsWhileAttendanceRemainsMonthly() {
        val test=row("term",mapOf("date" to "2026-09-01","assessmentType" to "1st Term","totalMarks" to 50))
        assertEquals("First Term",ParentReportPolicy.build(data(tests=listOf(test)),"s","October 2026").sections[2].title)
        assertTrue(ParentReportPolicy.build(data(tests=listOf(test)),"s","October 2026",false).sections[2].rows.isEmpty())
    }
    @Test fun sessionNameAliasesWorkAndHistoricalMembershipDoesNotUseCurrentClass() {
        val test=row("pastClass",mapOf("classId" to "past","academicSessionId" to "2026–2027","assessmentType" to "Class Test"))
        assertEquals(1,ParentReportPolicy.build(data(tests=listOf(test)),"s","October 2026").sections[2].rows.size)
    }
    @Test fun monthlyFeesAreScopedToStudentSessionAndMonthAndKeepPartialPayments() {
        fun bill(id:String,studentId:String="a",session:String="s",month:String="October 2026")=DiaryItem(id,mapOf("studentId" to studentId,"academicSessionId" to session,"month" to month,"feeType" to "Tuition","netPayable" to 2800.0,"paidAmount" to 1000.0))
        val source=data().copy(fees=listOf(bill("own"),bill("other",studentId="b"),bill("prior",month="September 2026"),bill("old",session="old")))
        val section=ParentReportPolicy.build(source,"s","October 2026").sections.single { it.title=="Monthly Fees & Funds" }
        assertEquals(1,section.rows.size);assertEquals("2800",section.metrics.toMap()["Billed"]);assertEquals("1000",section.metrics.toMap()["Paid"]);assertEquals("1800",section.metrics.toMap()["Outstanding"])
    }
    @Test fun profileRemarksRemainRealAndUnmarkedDatesAreNotInvented() {
        val source=data().copy(student=student.copy(raw=student.raw+("remarks" to "Discuss reading practice")))
        val report=ParentReportPolicy.build(source,"s","October 2026",false)
        assertEquals("Discuss reading practice",report.sections.single { it.title=="Profile Remarks" }.rows.single().single())
        assertTrue(report.sections.first().rows.isEmpty())
    }
}
