package pk.edunexa.app.operations

object OwnerPolicy {
    // A named conversion avoids shadowing Kotlin String?.orEmpty in the Owner UI.
    fun displayText(value: Any?): String = value?.toString() ?: ""
    fun allowed(uid:String?,actorUid:String,claims:Map<String,Any?>,account:Map<String,Any?>):Boolean = uid==actorUid && uid!=null && claims["platformOwner"]==true && account["role"] in setOf("owner", "admin") && account["active"]==true && account["portalDisabled"]!=true
    fun validateChange(ownSchoolId:String,targetSchoolId:String,enabled:Boolean) {
        require(targetSchoolId.matches(Regex("[A-Za-z0-9_.-]{1,128}")) && targetSchoolId !in setOf(".","..")) { "Invalid school identifier." }
        require(enabled || ownSchoolId!=targetSchoolId) { "You cannot disable your own linked school from this panel." }
    }
    fun setup(settings:Map<String,Any?>):String = when {
        settings["schoolName"]?.toString().isNullOrBlank() -> "School profile needed"
        settings["campusName"]?.toString().isNullOrBlank() -> "Campus needed"
        settings["schoolShortName"]?.toString().isNullOrBlank() -> "School short form needed"
        else -> "School profile ready"
    }
}
