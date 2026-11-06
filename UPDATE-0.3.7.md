# EduNexa Android 0.3.7 / code 20 — audit fixes

- Student monthly attendance and local activity alerts now select the latest saved record before filtering marked statuses. A newer Unmarked record cannot revive an old Present/Absent entry.
- Attendance correction notices use lastUpdatedAt/updatedAt ahead of original markedAt.
- Teacher Submit for review and Admin Return/Lock/Publish/Unlock controls now call the same trusted edunexaResultAction used by Desktop. Requests retain their original operation ID, reason and revisions in device-private storage across uncertain responses. Definitive authorization/validation/stale-state rejections clear the retry; uncertain transport failures retain it.
- Backend includes the trusted result handler plus the missing updateStudentCredentials callable used by the existing Student login editor. It verifies active school Admin, exact UID/profile/school links and Owner exclusion; passwords never enter Firestore. Auth and Firestore are separate systems: partial failure is explicitly reported and never described as a rollback or success.
- Push remains off by default. After approved deployment and device verification, a build may opt in with -PEDUNEXA_ACTIVITY_PUSH_ENABLED=true. This test APK does not enable process-closed push.

No Firebase deployment, billing change or production data change is performed by this update. Result publication, student credential changes and FCM require deployed trusted Functions. Existing package pk.edunexa.app and update keystore are retained. Install over the current app; do not uninstall or clear school data.

See the accompanying verification report for current executed checks and device-testing limits. Automated policy/backend/emulator checks do not substitute for Android UI and live account acceptance.
