package pk.edunexa.app.notifications
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
class NotificationPolicyTest {
    private val now = 1791532800000L
    private fun notice(status: String = "Published", publish: String = "2026-10-01T00:00:00.000Z", expiry: String = "") = DiaryItem("n", mapOf("status" to status, "publishAt" to publish, "expiresAt" to expiry, "recipientIds" to listOf("one")))
    @Test fun recipientIsolationAndPublishedVisibility() { assertTrue(NotificationPolicy.visible(notice(),false,"one",now)); assertFalse(NotificationPolicy.visible(notice(),false,"two",now)); assertFalse(NotificationPolicy.visible(notice(),false,"",now)) }
    @Test fun draftAndArchivedAreHiddenFromRecipients() { assertFalse(NotificationPolicy.visible(notice("Draft"),false,"one",now)); assertFalse(NotificationPolicy.visible(notice("Archived"),false,"one",now)); assertTrue(NotificationPolicy.visible(notice("Draft"),true,"",now)) }
    @Test fun futureScheduledAndExpiredAreHidden() { assertFalse(NotificationPolicy.visible(notice("Scheduled","2027-01-01T00:00:00.000Z"),false,"one",now)); assertFalse(NotificationPolicy.visible(notice(expiry="2026-10-02T00:00:00.000Z"),false,"one",now)); assertTrue(NotificationPolicy.visible(notice("Scheduled"),false,"one",now)) }
    @Test fun invalidPublishAndExpiryFailClosed() { assertFalse(NotificationPolicy.visible(notice(publish="bad"),false,"one",now)); assertFalse(NotificationPolicy.visible(notice(expiry="bad"),false,"one",now)) }
    private val teachers = listOf(DiaryItem("t", mapOf("status" to "Active", "inchargeClassId" to "c", "inchargeSectionId" to "a", "inchargeAcademicSession" to "s")), DiaryItem("inactive", mapOf("status" to "Inactive")))
    private val pupils = listOf(DiaryItem("a", mapOf("status" to "Active", "academicSessionId" to "s", "classId" to "c", "sectionId" to "a")), DiaryItem("b", mapOf("status" to "On Leave", "academicSessionId" to "s", "classId" to "c", "sectionId" to "b")), DiaryItem("old", mapOf("status" to "Active", "academicSessionId" to "old", "classId" to "c", "sectionId" to "a")))
    @Test fun allTargetsActiveTeachersAndCurrentSessionStudentsOnly() { assertEquals(setOf("t","a","b"), NotificationPolicy.recipients("All", teachers,pupils,setOf("s"),"","","").toSet()) }
    @Test fun classTargetsOnlyItsStudentsAndCurrentIncharge() { assertEquals(setOf("t","a"), NotificationPolicy.recipients("Class", teachers,pupils,setOf("s"),"c","a","").toSet()); assertEquals(setOf("b"), NotificationPolicy.recipients("Class", teachers,pupils,setOf("s"),"c","b","").toSet()) }
    @Test(expected = IllegalArgumentException::class) fun individualRejectsInactiveProfile() { NotificationPolicy.recipients("Individual", teachers,pupils,setOf("s"),"","","inactive") }
}
