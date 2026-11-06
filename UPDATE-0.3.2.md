# EduNexa 0.3.2 — test candidate (versionCode 15)

These are source changes, not a new signed APK/AAB. Do not publish until the Android build and device acceptance checks below pass.

## Changes

- Use the same explicitly Current / otherwise Active academic session in Admin curriculum, Teacher assignments, attendance, diary and exams. Previously Admin could save against Active while the Teacher found no Current session and displayed zero assignments.
- Teacher cards show session names and assignment counts, rather than opaque session IDs. Missing labels show an unavailable label instead of internal IDs.
- Class subject selection is now local and immediate. One Save Assignments action commits the selected subjects and updates teacher projections together. Existing teacher-linked subjects cannot be silently removed; concurrent curriculum changes produce a refresh/review error.
- Subject Management uses one scrolling list, including filters and scope controls, with Save/Discard actions outside the list. Failed saves preserve the selection.
- Fee cards show a readable due date and a consistently formatted voucher reference. Formatting is display-only: saved voucher/receipt identities and payment records are preserved.
- Attendance and fee lookup share a native QR screen with front/back camera switching, camera permission handling and cancellation. Selected camera persists between scans; the existing multiple-attendance-scan flow remains. Scanning a fee card still only opens bills and never confirms payment.
- New Student and Teacher forms still suggest registration/employee numbers automatically, but allow editing before login creation. They also allow editing the suggested full login email. The chosen email is used in Firebase account creation, with duplicate/collision errors preserved.
- Student admission uses a canonical registration lock in the existing academicConfig collection for concurrent new admissions; the usual duplicate and roll checks remain.
- Same first name already produces different login emails when registration numbers differ. No account rename or password reset is performed as part of these fixes.

## Existing accounts

Existing linked Firebase login email changes are **not implemented** in this candidate. A school profile edit alone cannot safely rename another Firebase Auth account. Existing login emails remain unchanged. Linked teacher employee IDs remain protected. This limitation must not be described as completed account-rename support.

New registrations are editable before initial save. After a create/link attempt, credential identity is held stable for retries. If email creation fails because another account already owns it, close/reopen the saved profile and use the existing Login Details workflow to create/link an unused email. Do not delete live accounts to work around a collision.

## Validation performed here

- 10 Kotlin/JUnit release regression tests passed: Active session without Current flag; explicit Current precedence; empty/closed-only configuration; repeated first names; chosen custom emails; non-English names with custom email; malformed email rejection; subject scope preservation/idempotency; reference/date formatting.
- Native QR activity compiled with Kotlin 2.4.10 against Android API classes, ZXing Embedded 4.3.0 and ZXing Core 3.5.3.
- Kotlin syntax parsing passed for project source/tests/build scripts.
- Campus isolation script passed.
- Full Android Gradle test/build attempt failed **before compilation**, because the wrapper could not reach services.gradle.org. These targeted checks are not a complete Android build or a device test.
- No Firebase rules, live records, cloud deployments or notification infrastructure were changed. Push remains disabled as in the input build; enabling it requires separate backend verification.

## Laptop build and device checks

1. Keep the previous project and privately back up the existing `edunexa-upload` key and passwords. Never create a replacement key for this update.
2. Extract this ZIP into a separate folder, open `EduNexa` in Android Studio and let Gradle sync finish. The QR library dependency has changed, so one new download is expected.
3. Run `gradlew.bat :app:testDebugUnitTest` in the project terminal. Then generate a signed **release APK** with the same upload key used for the recently installed 0.3.1 release. VersionCode 15 should install as an update over that release; the old debug-signed app still uses a different key.
4. In Admin, confirm the school session name appears in Subject Management. Select several subjects: ticks should respond immediately, Save should remain reachable, Discard should reset, and reopening should show saved selections.
5. Confirm Iqra's saved class/subject assignment appears under Teacher after refresh or a fresh login. Check attendance class access, diary subject selection and Tests/Marks access. Confirm another teacher cannot access unassigned classes.
6. Check fee due-date/reference display and bill printing. Confirm existing payment totals/history have not changed.
7. Scan a test student card with back camera, switch to front camera and scan again. Check camera denial, Close/Back cancellation, missing camera and multiple-scan duplicate handling. Fee QR must open the student's bills without receiving payment.
8. Create a test Student/Teacher using the auto number, then another using a manual unused number and customized email. Confirm login uses exactly the chosen email. Check duplicate numbers/emails are rejected. Do not share passwords in screenshots.
9. Only after fixes compile and these checks pass, generate the final signed AAB using the same key. The previously generated 0.3.1 AAB does not contain these fixes.
