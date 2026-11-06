package pk.edunexa.app.activity
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryPolicy
class ActivityHistoryTest {
    @Test fun capturesActorAndFieldNamesWithoutValuesOrPasswords() {
        val patch=ActivityHistory.patch(emptyMap(),mapOf("status" to "Absent","portalPassword" to "NEVER-STORE"),"uid","Teacher","teacher","Attendance updated","Pupil","2026-10-20T00:00:00Z")
        val event=DiaryPolicy.maps(patch["activityHistory"]).single()
        assertEquals("uid",event["actorUid"]);assertEquals(listOf("status"),event["fields"]);assertFalse(patch.toString().contains("NEVER-STORE"))
    }
    @Test fun metadataOnlyResaveDoesNotAddHistory() {assertTrue(ActivityHistory.patch(mapOf("status" to "Present"),mapOf("status" to "Present","updatedAt" to "new"),"u","Admin","admin","Updated","Pupil").isEmpty())}
    @Test fun keepsLatestHundredAndNeverMutatesPreviousList() {
        val old=(1..100).map{mapOf("id" to it.toString())}
        val before=mapOf("status" to "Absent","activityHistory" to old)
        val history=DiaryPolicy.maps(ActivityHistory.patch(before,mapOf("status" to "Present"),"u","Admin","admin","Override","Pupil")["activityHistory"])
        assertEquals(100,history.size);assertEquals("2",history.first()["id"]);assertEquals("1",old.first()["id"])
    }
}
