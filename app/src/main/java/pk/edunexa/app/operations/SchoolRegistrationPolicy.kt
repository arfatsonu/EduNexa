package pk.edunexa.app.operations

import pk.edunexa.app.auth.StudentLoginIdentity

data class SchoolRegistration(val schoolId:String,val schoolName:String,val campusName:String,val shortName:String,val adminName:String,val adminEmail:String)
object SchoolRegistrationPolicy {
    fun validate(input:SchoolRegistration,password:String):SchoolRegistration {
        val data=input.copy(schoolId=input.schoolId.trim(),schoolName=input.schoolName.trim(),campusName=input.campusName.trim(),shortName=StudentLoginIdentity.shortName(input.shortName),adminName=input.adminName.trim(),adminEmail=input.adminEmail.trim().lowercase(java.util.Locale.US))
        require(data.schoolId.matches(Regex("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]"))) { "School ID must be 3–64 lowercase letters, digits or hyphens." }
        require(listOf(data.schoolName,data.campusName,data.adminName).all{it.length in 2..150}) { "School, campus and Admin names must be 2–150 characters." }
        require(data.adminEmail.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) && data.adminEmail.length<=200) { "Enter the school Admin's real email address." }
        require(password.length in 12..100 && password.any{it.isUpperCase()} && password.any{it.isLowerCase()} && password.any{it.isDigit()}) { "Admin password needs 12–100 characters, uppercase, lowercase and a number." }
        return data
    }
    fun emptyConfig():Map<String,Any?> = listOf("classes","sections","sessions","subjects","teacherAssignments","classSubjectAssignments","students","teachers").associateWith{emptyList<Map<String,Any?>>()}
}
