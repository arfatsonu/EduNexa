package pk.edunexa.app.users
import org.junit.Assert.*
import org.junit.Test
import pk.edunexa.app.diary.DiaryItem
class UserAccessPolicyTest {
    @Test fun portalDisableIsIndependentOfAcademicStatus() { assertFalse(UserAccessPolicy.effectiveActive("teacher","Active",true)); assertTrue(UserAccessPolicy.effectiveActive("teacher","Active",false)); assertFalse(UserAccessPolicy.effectiveActive("teacher","Inactive",false)); assertFalse(UserAccessPolicy.effectiveActive("student","Inactive",false)); assertTrue(UserAccessPolicy.effectiveActive("student","On Leave",false)) }
    @Test(expected=IllegalArgumentException::class) fun ownAccountCannotBeChanged() { UserAccessPolicy.validateTarget("admin","admin","teacher") }
    @Test(expected=IllegalArgumentException::class) fun adminAccountCannotBeChanged() { UserAccessPolicy.validateTarget("admin","other","admin") }
    @Test fun profileWithoutLoginIsListedAsMissingWithoutFabricatedUid() { val rows=ManagedAccountPolicy.build(emptyList(),listOf(DiaryItem("t",mapOf("name" to "Teacher","status" to "Active"))),emptyList()); assertEquals("Missing Login",rows.single().status); assertNull(rows.single().uid); assertFalse(rows.single().manageable) }
    @Test fun duplicateLinksAreBlockedForReview() { val profile=DiaryItem("t",mapOf("name" to "Teacher","status" to "Active"));val users=listOf("u1","u2").map { DiaryItem(it,mapOf("role" to "teacher","profileId" to "t","active" to true)) };val account=ManagedAccountPolicy.build(users,listOf(profile),emptyList()).single();assertEquals("Link needs review",account.status);assertFalse(account.manageable) }
    @Test fun orphanedAndMismatchLinksAreNeverManaged() { val orphan=DiaryItem("u",mapOf("role" to "teacher","profileId" to "missing"));assertFalse(ManagedAccountPolicy.build(listOf(orphan),emptyList(),emptyList()).single().manageable);val mismatched=ManagedAccountPolicy.build(listOf(DiaryItem("u",mapOf("role" to "teacher","profileId" to "t"))),listOf(DiaryItem("t",mapOf("authUid" to "other"))),emptyList()).single();assertTrue(mismatched.broken) }
    @Test fun disabledLinkedLoginIsNotShownAsActive() { val user=DiaryItem("u",mapOf("role" to "student","profileId" to "s","active" to false,"portalDisabled" to true,"email" to "saved@student.a2campus.app"));val account=ManagedAccountPolicy.build(listOf(user),emptyList(),listOf(DiaryItem("s",mapOf("fullName" to "Student","status" to "Active")))).single();assertEquals("Portal Disabled",account.status);assertEquals("saved@student.a2campus.app",account.email);assertTrue(account.manageable) }
}
