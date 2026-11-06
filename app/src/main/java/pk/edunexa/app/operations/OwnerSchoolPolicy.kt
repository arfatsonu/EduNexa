package pk.edunexa.app.operations

import pk.edunexa.app.settings.BrandingPolicy
import pk.edunexa.app.settings.SchoolSettingsPolicy

object OwnerSchoolPolicy {
    val fields = SchoolSettingsPolicy.profileFields.keys + "schoolLogo"
    fun patch(original: Map<String, Any?>, input: Map<String, Any?>): Map<String, Any?> {
        val profile = SchoolSettingsPolicy.validateProfile(input)
        require(profile["schoolShortName"].toString().length in 2..12) { "School short form must contain 2–12 letters or digits." }
        val logo = BrandingPolicy.validate(BrandingPolicy.editable(original) + ("schoolLogo" to input["schoolLogo"]))["schoolLogo"]
        return (profile + ("schoolLogo" to logo)).filter { (key, value) -> value != (original[key] ?: "") }
    }
    fun merge(current: Map<String, Any?>, original: Map<String, Any?>, patch: Map<String, Any?>): Map<String, Any?> {
        require(patch.keys.all { it in fields }) { "Only school profile fields may be edited here." }
        check(patch.keys.all { current[it] == original[it] || current[it] == patch[it] }) { "This school was edited on another device. Close and refresh before saving." }
        return current + patch
    }
    fun email(value: String): String = value.trim().lowercase(java.util.Locale.US).also {
        require(it.length <= 200 && it.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "Enter a valid Admin email." }
    }
    fun admin(profile: Map<String, Any?>, uid: String, schoolId: String, ownerUid: String) {
        check(uid != ownerUid && profile["role"] == "admin" && profile["schoolId"] == schoolId && profile["active"] == true && profile["portalDisabled"] != true) { "This login is not an active Admin for this school." }
    }
    fun password(value: String, confirmation: String) {
        require(value == confirmation) { "New passwords do not match." }
        require(value.length in 12..100 && value.any(Char::isUpperCase) && value.any(Char::isLowerCase) && value.any(Char::isDigit)) { "Use 12–100 characters, uppercase, lowercase and a number." }
    }
}
