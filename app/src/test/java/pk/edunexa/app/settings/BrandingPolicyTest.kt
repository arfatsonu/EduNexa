package pk.edunexa.app.settings

import org.junit.Assert.*
import org.junit.Test

class BrandingPolicyTest {
    @Test fun defaultsAndEveryPresetRemainReadable() {
        assertEquals(SchoolBranding(), BrandingPolicy.read(emptyMap()))
        BrandingPolicy.presets.values.forEach { colors ->
            val input = mapOf("brandPrimary" to colors[0], "brandAccent" to colors[1], "brandBackground" to colors[2])
            assertEquals(colors[0],BrandingPolicy.validate(input)["brandPrimary"])
            assertTrue(BrandingPolicy.contrast(colors[0],colors[2]) >= 4.5)
        }
    }
    @Test fun malformedAndUnreadableColorsCannotBeSaved() {
        for(input in listOf(mapOf("brandPrimary" to "red"),mapOf("brandPrimary" to "#FFFFFF"),mapOf("brandBackground" to "#010101"))) {
            assertTrue(runCatching { BrandingPolicy.validate(input) }.isFailure)
            assertEquals(SchoolBranding().primary,BrandingPolicy.read(input).primary)
        }
        assertEquals("#172554",BrandingPolicy.validate(mapOf("brandPrimary" to " #172554 "))["brandPrimary"])
    }
    @Test fun rejectsUnsupportedIconsAndUnsafeOrOversizedImages() {
        for(input in listOf(mapOf("launcherIcon" to "uploaded"),mapOf("schoolLogo" to "https://unknown/logo"),mapOf("schoolLogo" to "data:image/png;base64,iVBORw0KGgo" + "A".repeat(120_000))))
            assertTrue(runCatching { BrandingPolicy.validate(input) }.isFailure)
        assertEquals("",BrandingPolicy.read(mapOf("schoolLogo" to "bad")).logo)
        assertEquals("default",BrandingPolicy.read(mapOf("launcherIcon" to "bad")).launcherIcon)
    }
    @Test fun brandingMergePreservesOtherSettingsAndDetectsConflicts() {
        val original = mapOf<String,Any?>("schoolName" to "My School", "lateCountsAsPresent" to true, "futureSetting" to 23)
        val current = original + ("lateCountsAsPresent" to false)
        val patch = BrandingPolicy.validate(mapOf("launcherIcon" to "navy"))
        val saved = SchoolSettingsPolicy.mergeSection(current,original,patch)
        assertEquals("My School",saved["schoolName"])
        assertEquals(false,saved["lateCountsAsPresent"])
        assertEquals(23,saved["futureSetting"])
        assertEquals("navy",saved["launcherIcon"])
        assertTrue(runCatching { SchoolSettingsPolicy.mergeSection(current + ("launcherIcon" to "gold"),original,patch) }.isFailure)
        assertTrue(runCatching { SchoolSettingsPolicy.mergeSection(current,original,mapOf("unknown" to true)) }.isFailure)
    }
}
