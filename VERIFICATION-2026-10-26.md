# EduNexa verification and fixes — 26 October 2026

Android 0.3.7 / code 20 and Desktop 0.3.2 are test releases. The checks below were executed in this audit. Full live/device acceptance and publication approval remain pending.

## Changes made

1. **Attendance totals — Android and Desktop.** Monthly Student attendance now selects the newest saved row for each date before filtering statuses. A newer Unmarked record no longer resurrects an older Present/Absent record or changes the attendance percentage incorrectly. Local activity notices follow the same rule.
2. **Attendance correction alerts — both clients.** The current correction timestamp takes precedence over the original marking timestamp. A correction made while a user is signed in can qualify as a fresh update.
3. **Windows scheduled alerts.** Future notices are no longer marked seen before their publication time. A timer checks due scheduled notices without requiring a new database snapshot. Expired/invalid-expiry notices cannot alert. Unmounting stops the timer.
4. **Android result-review controls.** Teacher Submit for review and Admin Return, Lock, Publish and Unlock now call the same trusted server transaction used by Desktop. Android previously had no corresponding action controls wired. Exact operation IDs, reasons and revisions survive uncertain responses in device-private storage. A failed/unavailable server never produces a success message. Lock does not hide Publish; Unlock withdraws published personal result copies through the server.
5. **Student login update backend.** Android's existing login editor referenced updateStudentCredentials, which was absent from the supplied backend. The handler is now included in both backend source packages. It verifies active school Admin, exact school/profile/Auth UID linkage and Owner exclusion, then updates Auth and saved email metadata. Passwords are never saved in Firestore. Auth and Firestore cannot share one atomic transaction: partial failure explicitly reports that the login may already have changed and requires verification before retry.
6. **Release configuration.** Visible Desktop version labels match the actual package. Android push remains off by default; a validated build property provides an explicit opt-in after approved backend deployment. No billing change was made.

## Executed checks

| Check | Result | What this establishes |
|---|---|---|
| Android Gradle build | PASS | testDebugUnitTest and assembleDebug completed successfully |
| Android unit tests | 280 passed / 52 suites; 0 failures, errors or skips | Existing policies plus attendance and request-retry regressions compile and pass |
| Android package metadata | PASS | pk.edunexa.app; version 0.3.7; code 20; min API 24; target/compile API 36 |
| Android APK signature | PASS | APK verifies and certificate SHA-256 matches the saved 0.3.6 test APK |
| Android project-isolation/Owner regression script | PASS | Existing EduNexa isolation and Owner-card conversion guards pass |
| Android backend tests | 31 passed | Activity, Owner provisioning, result callable authentication and credential-handler authorization/uncertainty cases |
| Desktop logic tests | 89 passed | Attendance, fees/receipts/retries, imports, role/school scope, academic and result policies |
| Desktop existing UI scenarios | 39 passed | Synthetic Owner/Admin/Teacher/Student navigation and workflows, laptop four/five-column layout, photos/fallbacks, QR image decode, fees/receipts, Excel import/recovery, marks and results |
| New Desktop alert-hook UI regressions | 2 passed | Real hook timer: scheduled delivery once without another snapshot, cleanup and expired-notice exclusion |
| Desktop backend tests | 24 passed | Callable authentication, activity delivery/retry and credential-handler tests; many overlap Android's shared backend tests |
| Firestore emulator rules/result integration | 24 passed | Actual local SDK transactions: role/school isolation, submitted/locked/published zero and Exempt marks, replay, private projections and atomic withdrawal |
| Desktop production build/boot | PASS | Vite production build; actual Firebase SDK boots with 0 renderer errors and 0 external requests before sign-in; fixture content excluded |
| Windows package integrity | PASS | Packaged app version 0.3.2 and all six packaged renderer/Electron files exactly match current built files |
| Standalone mobile-browser preview | PASS | Production renderer starts in 390×844 mobile emulation at 1280px desktop layout; password toggle/crypto work; 0 startup errors |
| Windows installer | Built | Unsigned x64 test installer; physical Windows execution is not implied |

Browser UI checks use synthetic fixtures. Emulator writes use demo-edunexa-rules on localhost. These checks do not authenticate to real school accounts, upload real photos, or change live school records.

Android signing certificate SHA-256: `5cac54d22171a47c875602c8643a03a1c6411c055b099f64eb56edc407c4a5b6`.

## Remaining release gates

- **Backend deployment and real account tests.** Result publication, Android push registration/delivery and student credential updates require trusted Functions deployed in the existing edunexa-portal project. No authenticated deployment or current production-endpoint acceptance was performed in this audit. The previous report recorded missing result/push endpoints; their current live state has not been rechecked here.
- **Billing decision.** The source deployment instructions flag Functions deployment as requiring an eligible plan; this audit did not link billing or upgrade Spark. Publishing an APK/installer alone does not deploy the server. After approved deployment, Android process-closed push also requires an opt-in rebuild with `-PEDUNEXA_ACTIVITY_PUSH_ENABLED=true`; this delivered APK keeps push disabled. In-app updates remain available through authorized reads.
- **Physical Android acceptance.** Install this APK over 0.3.6 without uninstalling/clearing data. Verify real Owner/Admin/Teacher/Student sign-in, photos, camera QR scanning, fee receipts, printing/share, result controls and resume/back navigation. No Android emulator/device UI test was executed here.
- **Physical Windows acceptance.** Install/update, camera permissions/continuous scanning, physical printer/A4 output, tray lifecycle and real OS notifications require a Windows machine. Camera-image decoding and print-document generation passing in browser tests are narrower evidence.
- **Live shared-data acceptance.** Verify approved test records across Android/Desktop: actual Auth, Firestore/Storage, concurrent saves and school/role isolation. No real student, fee, attendance or announcement records were changed during this audit.
- **Publication.** The APK uses the existing test signing certificate; the Windows installer is unsigned. Store upload signing, release distribution/security reputation and store submissions are separate release work. No publication was performed.

## Delivery and deployment notes

- Android 0.3.7 test APK: preserve the current app/data; package and test certificate match 0.3.6.
- Windows 0.3.2 ZIP: contains the installer and this report.
- Both updated source archives include these fixes and backend source. Use one canonical Functions deployment source; do not independently deploy duplicate codebases. Android's backend retains its original student-activity codebase and Owner tooling.
- Live result actions verify real UID, role, school, assignment, roster, saved mark revisions and server content digest. Clients do not gain permission to write publishedResults/resultWorkflow directly.
- The report is a record of executed checks and their limits, not an unconditional claim that every screen/action has passed on real hardware.
