# EduNexa — isolated Android copy

## 0.3.6 Owner school management

See `UPDATE-0.3.6.md` for the redesigned registry, school identity editor and
verified Admin login management. Publish the matching Firestore rules to enable
Owner profile edits and Admin metadata access. Existing school data is retained.

## 0.3.5 Owner crash fix

See `UPDATE-0.3.5.md` for the confirmed StackOverflowError fix and update instructions.

## 0.3.4 startup test update

See `UPDATE-0.3.4.md` for neutral startup/Owner branding, preventing launcher
changes before a verified school session, local debug crash reports, and the
required phone check for the reported Owner startup closure.

Copied from Al-Nisa Android commit e91b01d3e70effee560f6edc01c8b2c022a6b431.
This branch is a separate product. Al-Nisa main and its live Firebase remain unchanged.

- Android package: `pk.edunexa.app`; installs alongside `pk.edu.alnisa.school`.
- Source configured for Firebase project `edunexa-portal`; updated debug APK built successfully. No demo accounts or imported school data.
- Existing Admin, Teacher and Student modules are retained. School branding comes from each authenticated user's school settings.
- Student serial prefix: STU. New student accounts use first name + numeric registration number + the configured school short form, e.g. `ali102@ans.edu`. Set School Settings → School Profile → School Short Form before registering/importing new students. Choose a distinct short form for each school; Firebase rejects duplicate login emails. Existing accounts retain their original identity. New teachers use first name + .t + the full employee number at the school-short-form domain, e.g. haba.t001@ans.edu. Password generation is unchanged.
- Sign in uses the full school-issued email; registration numbers alone are ambiguous across schools.
- EduNexa Firebase configuration is included. Build rejects Al-Nisa production project ID.
- School setup screen remains visible until a separate backend is configured.

## Activate the independent product

The separate Firebase project `edunexa-portal` and Android package `pk.edunexa.app` are registered.
Enable email/password Authentication, Firestore and Storage. Its Android `google-services.json`
is included at `app/google-services.json`; never use the Al-Nisa project.
Deploy the copied rules under `backend` to the new project explicitly. Do not reuse Al-Nisa deployment workflows or credentials.
Create each school's real document/settings, school-scoped academic config and counters, and its Auth admin/user profile
with a trusted server-side provisioning process. Classes, sections and subjects must start empty and be entered by that school.
Do not allow client self-assignment of school IDs or admin roles.

Required empty school config:
`schools/{schoolId}/academicConfig/current` needs arrays `classes`, `sections`, `sessions`,
`subjects`, `teacherAssignments`, `classSubjectAssignments`, `students`, `teachers`.
Counter docs `registrationCounter.lastAdmissionNumber` and `teacherRegistrationCounter.lastEmployeeNumber` start at 0.
`users/{authUid}` needs `role: admin`, `schoolId`, `active: true`, `portalDisabled: false`, name and email.
Admin creates the actual current session and academic structure through existing screens.

Before production, verify two real test schools cannot read/write each other's records, files or users,
including Teacher and Student scope, with Firebase emulator integration tests.
The copied rules already use school-scoped authorization but this copy is not yet verified against a new live backend.
Notifications require the independent Functions deployment; no push delivery is claimed by this initial APK.
Debug signing is for testing only. Play Store publication requires a release signing key and AAB.

## Build status
Updated debug APK built successfully with Gradle 9.6.0 and Android SDK 36. Existing unit tests and package/Firebase isolation checks passed. APK signature verified. Live login, two-school backend isolation and on-device UI still require verification after Firebase services and real school accounts are provisioned.
Use `./gradlew :app:assembleDebug` with JDK 17 and Android SDK installed.

## Version 0.1.3

- Student account generation always requires the configured school short form; there is no legacy hashed-email fallback for new students. Existing account email/UID/password links are preserved on import retries.
- The student branding header shows school and campus; the welcome card shows the student's name. Student session displays use a saved readable label and hide opaque database IDs. New enrollments retain the session name alongside its canonical ID.
- Drawer workspace/session text is stacked and constrained to avoid single-character wrapping.
- Branding offers Use School Logo on Home Screen for a saved uploaded logo. On supported Android launchers this requests a pinned shortcut and the user confirms Add. Existing pinned icons refresh when the saved logo changes. The app drawer still uses one of the compiled icon aliases.
- Package pk.edunexa.app, versionCode 4. Signed with the original update key. Clean APK build succeeded; 195 unit tests passed. No device/launcher or live Firebase account test was run in this build environment.

## Version 0.1.4

Preserve every numeric registration digit, including leading zeros: STU-008 / 008 produces khizar008@ans.edu. No registration-to-integer conversion is used. Existing Auth email addresses are not migrated. Build and 196 unit tests passed.

## Version 0.1.5

New teacher accounts use first name, a .t role marker, every employee-number digit and the school's configured short form. T-001 becomes haba.t001@ans.edu. Manual teacher registration, Create Login suggestions and Excel imports use this format. The role marker separates teacher and student accounts with the same name/number. Removed-account collisions receive a short numeric suffix rather than a long profile identifier. Existing linked accounts retain their saved email on retries/imports.

Version 0.1.5 validation: clean APK build succeeded; 200 unit tests passed. Device/live login verification remains pending.

## EduNexa 0.1.7 in-app activity alerts

Attendance status, fee receipt and homework assignment/check alerts are rendered
from the student's existing authorized Firebase records. They appear in the
Notifications feed and bell; new activity while the portal is open also shows a
View snackbar. No billing upgrade or Functions deployment is needed for this mode.
Activity read state is local to this phone. Android status-bar push is disabled;
optional server push code is retained, but is not deployed by this update.

## EduNexa 0.1.8 automatic QR card attendance

Admin > Students > open a profile > QR Attendance Card > Print / Save PDF.
The card contains a versioned EduNexa school/student identifier, not a password,
email address or biometric. Existing non-EduNexa cards need this QR printed on them.

Admin > Attendance (today) > Scan Card · Auto Attendance. A valid card immediately
saves Present in the existing attendance collection after online authorization,
current student/session/class validation and a transaction. No second confirmation
or whole-class Save is required. Existing Present/Absent/Leave/Late records are
reported as already marked and retained. Each new scan is started with Scan Card.
The class teacher's manual workflow and permissions are unchanged. The scanner
requires the Admin login and uses Asia/Karachi dates; student accounts cannot mark
attendance remotely. It reads the current student document, not a stale directory
projection. No new Firestore rules, Functions or Blaze upgrade are required.

As of 0.3.2, scanning uses the shared native ZXing camera screen with front/back
switching and an explicit CAMERA permission request. Live attendance saves still
require internet. Scanning requires a phone camera; automated tests do not
substitute for real-phone QA.
No scan is reported as saved before the Firestore transaction is acknowledged.
QR cards can be copied: this identifies a card's student record, not the physical
person presenting it. Keep the school's scanning phone supervised.

Live verification pending: print a real card; scan once; confirm Present in Admin,
Student attendance and the in-app alert feed; scan again and verify no duplicate.
Also test an already Absent/Leave/Late record, another school's card, an inactive
or deleted student, a changed academic session/class, a cancelled scan and network
failure/retry. Do not change the original Al-Nisa Firebase project or app.

### 0.1.9 session compatibility

Card scanning now uses EnrollmentPolicy.currentSession, like admissions and the
Admin dashboard. An Active session without isCurrent is supported; an explicitly
current legacy session without a status is supported too. Explicit Closed,
Inactive or Upcoming sessions remain blocked for scanning, and the student's
saved session ID/name must still match. No Firebase data migration is required.

### 0.1.10 multiple card scanning

Admin Attendance now has Scan Multiple Cards. After each server-acknowledged save or existing-record result, a short result message appears and Google Code Scanner opens again automatically. Remove the previous card before the next scan. Scanner Back/cancel ends the batch; failures pause scanning and show the error. The stop button during the result interval prevents reopening. Navigating away cancels the batch; existing attendance statuses remain unchanged. As of 0.3.2 this uses sequential native scanner launches, with the chosen camera remembered between scans.

### 0.2.0 combined school operations

See `UPDATE-0.2.0.md` for the seven-feature map, install/setup instructions, permissions, retention and device checks. Includes QR arrival time / configurable automatic Late, absent follow-up entry point, direct Excel import, monthly fees/remarks in complete reports, bulk QR cards and password-safe login slips, source-record activity history, and a verified custom-claim Owner registry with suspend/enable controls. Owner access requires publishing the bundled rules and granting the Auth claim; no live Firebase changes or Functions deployment are performed by the APK.

Rules regression tests are under `backend/rules-tests` (Node 22+, Java 21+, `npm ci`). From `backend/rules-tests`, run `node_modules/.bin/firebase emulators:exec --project demo-edunexa-rules --only firestore 'npm test'`. The bundled emulator configuration uses port 8088. Use only the demo project for these fixtures. Owner provisioning tool: `backend/functions/configure-owner.js`.

### 0.3.0 daily workflows and QR fee lookup

See `UPDATE-0.3.0.md`. Adds fee lookup using the existing student QR (no automatic payment), student-scoped fee reads, actionable role priorities, private user/school-scoped recovery drafts, retry payload verification, student timeline, consistent branded print elements, guided setup and verified Owner school registration. The school-creation transaction preserves existing data and links the actual secondary-Auth UID; interrupted Auth/Firestore provisioning is resumed with the same credentials. New rules must be published for Owner registration. Fresh empty academic setup can save its first session without bypassing protections for linked profiles/assignments. Java API desugaring supplies supported date/time APIs for the declared older Android minimum.

## 0.3.1

Compact Admin, Teacher and Student dashboards; school counts take priority and duplicated action cards are removed. See UPDATE-0.3.1.md for scope and verification limits.


## 0.3.2 test candidate

See `UPDATE-0.3.2.md` for assignment/session fixes, responsive subject selection, QR camera switching, new-login customization, validation results and remaining release checks.
