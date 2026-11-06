package pk.edunexa.app.diary

import org.junit.Assert.*
import org.junit.Test

class DiaryPolicyTest {
    @Test fun stableScopeIdentityIncludesSessionAndDate() {
        assertEquals(DiaryPolicy.key("session", "date", "class", "section", "subject"), DiaryPolicy.key("session", "date", "class", "section", "subject"))
        assertNotEquals(DiaryPolicy.key("old", "date", "class", "section", "subject"), DiaryPolicy.key("new", "date", "class", "section", "subject"))
        assertNotEquals(DiaryPolicy.key("ab", "c"), DiaryPolicy.key("a", "bc"))
    }
    @Test(expected = IllegalArgumentException::class) fun reversedDueDateRejected() {
        DiaryPolicy.validate("2026-10-09", "2026-10-08", "Topic")
    }
    @Test(expected = IllegalArgumentException::class) fun missingTopicRejected() {
        DiaryPolicy.validate("2026-10-09", "2026-10-10", " ")
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedAttachmentRejected() {
        DiaryPolicy.validateFile(PendingDiaryFile("file.pdf", "application/pdf", ByteArray(2 * 1024 * 1024)))
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedFileRejected() {
        DiaryPolicy.validateFile(PendingDiaryFile("file.exe", "application/octet-stream", byteArrayOf(1)))
    }
    @Test fun pdfAndImageAllowed() {
        DiaryPolicy.validateFile(PendingDiaryFile("file.pdf", "application/pdf", byteArrayOf(1)))
        DiaryPolicy.validateFile(PendingDiaryFile("photo.jpg", "image/jpeg", byteArrayOf(1)))
    }
}
