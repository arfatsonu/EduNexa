package pk.edunexa.app.workflow

import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.fees.FeePolicy
import pk.edunexa.app.students.EnrollmentPolicy

data class SetupStep(val title:String,val detail:String,val complete:Boolean,val destination:String)
object WorkflowPolicy {
    fun setup(settings:Map<String,Any?>,config:Map<String,Any?>,teachers:Int,students:Int):List<SetupStep> {
        val session=EnrollmentPolicy.currentSession(config)
        val active=session.isNotEmpty() && (session["status"]?.toString().equals("Active",true)||(session["isCurrent"]==true && session["status"]?.toString().isNullOrBlank()))
        val classes=DiaryPolicy.maps(config["classes"]).filter{it["isActive"]==true}
        val sections=DiaryPolicy.maps(config["sections"]).filter{it["isActive"]==true && classes.any{c->c["id"]==it["classId"]}}
        return listOf(
            SetupStep("School profile","Name, campus and short form",listOf("schoolName","campusName","schoolShortName").all{!settings[it]?.toString().isNullOrBlank()},"School Settings"),
            SetupStep("Academic session","Choose the active school year",active,"Classes & Sections"),
            SetupStep("Classes & sections","Add the real classes taught at this school",classes.isNotEmpty()&&sections.isNotEmpty(),"Classes & Sections"),
            SetupStep("Subjects","Add the school's real curriculum",DiaryPolicy.maps(config["subjects"]).any{it["isActive"]==true},"Subjects"),
            SetupStep("Teachers","Add staff and assign their classes",teachers>0,"Teachers"),
            SetupStep("Students","Add pupils or preview an Excel import",students>0,"Students")
        )
    }
    fun verifyPaymentRetry(history:List<Map<String,Any?>>,id:String,amount:Double,method:String,notes:String) {
        val previous=history.firstOrNull{it["id"]==id} ?: return
        check(FeePolicy.cents(FeePolicy.amount(previous,"amount"))==FeePolicy.cents(amount) && previous["paymentMethod"]==method && previous["notes"]?.toString().orEmpty()==notes) { "This request already saved a different payment. Refresh and check its receipt before starting another payment." }
    }
}

data class TimelineEvent(val id:String,val date:String,val type:String,val title:String,val detail:String)
object TimelinePolicy {
    fun build(student:DiaryItem,attendance:List<DiaryItem>,fees:List<DiaryItem>,diary:List<DiaryItem>,results:List<DiaryItem>):List<TimelineEvent> {
        val events=mutableListOf<TimelineEvent>()
        val admitted=student.text("admissionDate")
        if(admitted.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))events+=TimelineEvent("admission/${student.id}",admitted,"Admission","School admission",listOf(student.text("admissionNo"),student.text("className"),student.text("section")).filter{it.isNotBlank()}.joinToString(" • "))
        attendance.filter{it.text("studentId")==student.id}.groupBy{it.text("academicSessionId").ifBlank{it.text("academicSession")}+"/"+it.text("attendanceDate")}.values.mapNotNull{group->group.maxByOrNull{it.text("lastUpdatedAt").ifBlank{it.text("markedAt")}}}.forEach{r->
            if(r.text("status") in setOf("Present","Absent","Leave","Late"))events+=TimelineEvent("attendance/${r.id}",r.text("attendanceDate"),"Attendance",r.text("status"),listOf(r.text("arrivalTime").takeIf{it.isNotBlank()}?.let{"Arrival $it PKT"}.orEmpty(),r.text("remarks").ifBlank{r.text("note")},r.text("followUpComment")).filter{it.isNotBlank()}.joinToString(" • "))
        }
        fees.filter{it.text("studentId")==student.id}.forEach{bill->
            DiaryPolicy.maps(bill.raw["paymentHistory"]).forEachIndexed{i,p->events+=TimelineEvent("payment/${bill.id}/${p["id"]?:i}",p["paymentDate"]?.toString().orEmpty(),"Fees","Payment received • Rs. ${FeePolicy.amount(p,"amount")}",listOf(bill.text("feeType"),bill.text("month"),p["receiptNo"]?.toString().orEmpty()).filter{it.isNotBlank()}.joinToString(" • "))}
        }
        diary.filter{(it.raw["studentIds"] as? List<*>)?.contains(student.id)==true}.forEach{r->events+=TimelineEvent("diary/${r.id}",r.text("date"),"Homework",r.text("subject").ifBlank{"Diary"},r.text("homework").ifBlank{r.text("topic")}.take(300))}
        results.filter{it.text("studentId")==student.id && it.text("publishedAt").isNotBlank()}.forEach{r->events+=TimelineEvent("result/${r.id}",r.text("publishedAt").ifBlank{r.text("date")}.take(10),"Results",r.text("testTitle").ifBlank{"Published result"},listOf(r.text("subject"),r.text("status"),pk.edunexa.app.students.StudentPortalPolicy.resultScore(r)).filter{it.isNotBlank()}.joinToString(" • "))}
        return events.filter{it.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))}.distinctBy{it.id}.sortedWith(compareByDescending<TimelineEvent>{it.date}.thenBy{it.type}.thenBy{it.id})
    }
}
