# EduNexa 0.3.3 — separate platform Owner workspace

## Behavior
- Verified Owner credentials open the EduNexa Owner dashboard immediately, including after session restoration. There is no school prerequisite or school dashboard load.
- School Admin credentials open only the linked school's dashboard. Owner navigation and registration controls are absent from the school Admin menu.
- The public login role choices remain Admin, Teacher and Student. The platform Owner signs in through the Admin sign-in form; the trusted server claim determines the destination, never a client-selected role or email allowlist.
- Owner branding uses EduNexa rather than a previously cached school's logo/name.
- School registration creates its own Firebase Auth Admin and isolated Firestore school workspace.

## Activation (not performed by this source update)
Use a dedicated Firebase Auth account for the platform Owner. Keep school Admin accounts separate. The trusted setup helper creates its users/{actualAuthUid} profile if missing. No schoolId is needed. The Firebase Auth account must already exist.
Use trusted Admin SDK credentials with backend/functions/configure-owner.js to grant the platformOwner custom claim. Use an Auth UID or --email OWNER_EMAIL. The helper accepts only a standalone account: any schoolId/profileId link is rejected without altering that school Admin. It creates a missing Owner profile using the actual Auth UID or promotes an active standalone owner/admin profile. The server claim is granted only after the profile is ready. A failed claim write is reported and can be retried. Revoking the claim denies Owner access; it does not turn the account into a school Admin.
Publish backend/firestore.rules and backend/storage.rules before using the updated app. Sign out and back in after activation. Private keys must never be bundled into the app or sent in chat.

## Validation in this run
- JavaScript syntax checks passed for configure-owner.js and owner.rules.test.cjs.
- Backend function tests: 24 passed, 0 failed, including 7 new provisioning checks for dedicated Owner identities, school Admin preservation, disabled users, project mismatch, claim preservation, revocation and failed activation. These do not replace live login/device verification.
- Firestore rules tests: 13 passed, 0 failed, using a local demo emulator (Firebase CLI 13.35.1 / emulator 1.19.8 on Java 17). The original baseline passed 12/12. Reordered the Admin authorization check to avoid excessive repeated evaluation on teacher history rollover. Cases cover standalone Owner registration, denied school-admin Owner creation/promotion, role forgery, cross-school access and attendance history.
- Android build and unit tests succeeded: assembleDebug + testDebugUnitTest, Gradle 9.6.0, JDK 17, Android SDK 36. All 262 unit tests passed (0 failed, 0 skipped). Local build proxy/JDK/trust-store setup was required; these host-specific settings are not bundled into the project.
- APK verified: pk.edunexa.app, versionName 0.3.3, versionCode 16, minSdk 24, targetSdk 36. APK signature verification passed using the existing bundled debug signing key. This is a test APK, not a Play Store release bundle.
- Live Firebase permissions, deployment, installed app and real-device behavior were not verified or changed.

## Required device checks after build/deploy
1. Owner with no schoolId signs in and restores directly to Owner dashboard.
2. School Admin signs in/restores directly to its own school; no Owner menu/button/list appears.
3. A school Admin cannot list schools, forge Owner role, or read another school's students/fees/attendance.
4. Owner registers an empty school and its Admin can sign in with the provided credentials.
5. Owner sign-out followed by school Admin sign-in does not retain Owner controls or branding.
