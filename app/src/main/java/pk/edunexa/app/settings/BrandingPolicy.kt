package pk.edunexa.app.settings

import kotlin.math.pow

data class SchoolBranding(
    val schoolName: String = "EduNexa",
    val campus: String = "School Portal", val logo: String = "",
    val primary: String = "#0A0E17", val accent: String = "#E58A13",
    val background: String = "#F7F9FC", val launcherIcon: String = "default"
)

object BrandingPolicy {
    val fields = setOf("schoolLogo", "brandPrimary", "brandAccent", "brandBackground", "launcherIcon")
    val presets = linkedMapOf(
        "EduNexa" to listOf("#0A0E17", "#E58A13", "#F7F9FC"),
        "Emerald" to listOf("#064E3B", "#059669", "#F0FDF4"),
        "Royal Blue" to listOf("#172554", "#2563EB", "#EFF6FF"),
        "Burgundy" to listOf("#4C0519", "#BE123C", "#FFF1F2"),
        "Purple" to listOf("#3B0764", "#9333EA", "#FAF5FF")
    )
    private fun color(value: Any?, fallback: String) = value?.toString()?.trim()?.uppercase()?.takeIf { it.matches(Regex("#[0-9A-F]{6}")) } ?: fallback
    fun read(raw: Map<String, Any?>): SchoolBranding {
        val defaults = SchoolBranding()
        val colors = runCatching { validate(raw + mapOf("schoolLogo" to "", "launcherIcon" to "default")) }.getOrNull()
        return SchoolBranding(
            schoolName = raw["schoolName"]?.toString()?.takeIf { it.isNotBlank() } ?: defaults.schoolName,
            campus = raw["campusName"]?.toString()?.takeIf { it.isNotBlank() } ?: defaults.campus,
            logo = raw["schoolLogo"]?.toString()?.takeIf { validLogo(it) }.orEmpty(),
            primary = colors?.get("brandPrimary")?.toString() ?: defaults.primary,
            accent = colors?.get("brandAccent")?.toString() ?: defaults.accent,
            background = colors?.get("brandBackground")?.toString() ?: defaults.background,
            launcherIcon = raw["launcherIcon"]?.toString()?.takeIf { it in setOf("default", "navy", "gold") } ?: "default"
        )
    }
    fun editable(raw: Map<String, Any?>): Map<String, Any?> = read(raw).let {
        mapOf("schoolLogo" to it.logo, "brandPrimary" to it.primary, "brandAccent" to it.accent, "brandBackground" to it.background, "launcherIcon" to it.launcherIcon)
    }
    private fun luminance(hex: String): Double {
        val rgb = hex.removePrefix("#").chunked(2).map { it.toInt(16) / 255.0 }.map { if(it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722
    }
    fun contrast(a: String, b: String): Double { val x=luminance(a); val y=luminance(b); return (maxOf(x,y)+0.05)/(minOf(x,y)+0.05) }
    private fun validLogo(value: String) = value.isEmpty() || (value.length <= 120_000 && value.startsWith("data:image/png;base64,iVBORw0KGgo") && value.substringAfter("base64,").matches(Regex("[A-Za-z0-9+/]+={0,2}")))
    fun validate(input: Map<String, Any?>): Map<String, Any?> {
        val defaults = SchoolBranding()
        val primary = color(input["brandPrimary"], defaults.primary)
        val accent = color(input["brandAccent"], defaults.accent)
        val background = color(input["brandBackground"], defaults.background)
        listOf("brandPrimary", "brandAccent", "brandBackground").forEach { key ->
            require(input[key] == null || input[key].toString().trim().matches(Regex("#[0-9a-fA-F]{6}"))) { "Use a color such as #172554." }
        }
        require(contrast(primary,"#FFFFFF") >= 4.5 && contrast(primary,background) >= 4.5 && luminance(background) >= 0.8) { "Choose a dark primary color and a light background so text stays readable." }
        val logo = input["schoolLogo"]?.toString().orEmpty()
        require(validLogo(logo)) { "Choose a PNG school logo using the logo picker." }
        val icon = input["launcherIcon"]?.toString() ?: "default"
        require(icon in setOf("default","navy","gold")) { "Choose one of the included app icons." }
        return mapOf("schoolLogo" to logo, "brandPrimary" to primary, "brandAccent" to accent, "brandBackground" to background, "launcherIcon" to icon)
    }
}
