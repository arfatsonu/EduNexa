package pk.edunexa.app.academic

import pk.edunexa.app.diary.DiaryPolicy

object AcademicBootstrapPolicy {
    fun emptySchoolSetup(teachersBefore:Int,teachersAfter:Int,students:Int,config:Map<String,Any?>,profileWrite:Boolean):Boolean =
        teachersBefore==0 && teachersAfter==0 && students==0 && !profileWrite &&
        DiaryPolicy.maps(config["teacherAssignments"]).isEmpty() && DiaryPolicy.maps(config["classSubjectAssignments"]).isEmpty()
}
