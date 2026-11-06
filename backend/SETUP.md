# Independent EduNexa backend

Developer: Muhammad Arfat.

1. Create a separate Firebase project, enable Email/Password Authentication,
   Firestore and Storage, and register Android package `pk.edunexa.app`.
2. Download its `google-services.json` to `app/google-services.json`.
3. Deploy the rules from this directory explicitly to that project.
4. Create the real school's Admin email/password account in Firebase Authentication.
5. On a trusted workstation, install `firebase-admin` and configure Application
   Default Credentials for the new project. Keep credentials out of the APK and git.
6. Write a private JSON setup file containing `projectId`, `schoolId`, `schoolName`,
   `campusName`, `adminUid` (from Authentication), and `adminName`.
   Run `node backend/provision-school.mjs /path/to/school-setup.json` from the repo root.
7. Rebuild the Android app and sign in as Admin. Enter the school's own academic
   session, classes, sections and subjects. Repeat step 6 with a different school
   ID and Admin UID for each school.

Provisioning creates school settings, empty academic arrays, serial counters and
the Admin profile in one transaction. It rejects existing records instead of
overwriting a school's data or reassigning another school's administrator.
It uses the existing Auth account and never exports its password.

The app is configured for `edunexa-portal`. Student activity Functions are prepared
in `functions/`; deployment and live phone verification are still required.
Production release also requires live isolation verification and a signed release AAB.

## In-app activity alerts (0.1.7)

This APK uses existing authorized StudentPortalData (attendance, actual fee
paymentHistory receipts, assigned homework and individual homeworkFeedback) to
populate the student's Notifications feed and bell badge. New activity while
signed in also displays an in-app snackbar with View. No Functions, billing
upgrade, new database rules, synthetic Firestore notices or extra listeners are
required for this mode. Regular school announcements retain their existing
Firestore feed and read receipts.

Activity alerts reflect saved source records: attendance shows the latest saved
status for each date in the student's session, payment receipts remain separate,
and homework assignments/checks use the student's authorized class/session.
Deleting a source removes its derived activity view. Activity Read state is stored
on this device, separated by UID, school and profile, and survives normal app
updates/restarts but not clearing app data or reinstalling. It is not synchronized
across devices. Existing saved activity appears upon first loading this version;
initial loading does not display a burst of snackbar popups.

Push setup is disabled (`ACTIVITY_PUSH_ENABLED=false`); no permission prompt or
undeployed registerPushDevice call is needed in this APK. The backend below is
retained for a future explicitly chosen push deployment. After such a deployment,
set ACTIVITY_PUSH_ENABLED=true and rebuild, then run all live delivery checks.

## Optional student activity push notifications (0.1.6 backend)

Triggers watch saved attendance, fee paymentHistory, diaryEntries with homework,
and individual homeworkFeedback documents. Students receive persistent notices
in the existing school Notifications feed and high-priority FCM alerts on each
registered phone. Homework checks are included. No retroactive bulk alerts are
created by deployment; only subsequent saved changes generate events.

Attendance alerts occur on new Present/Absent/Leave/Late records and status
corrections, not reason-only edits. Fee alerts occur once per new payment ID,
including partial payments, not unpaid bill creation or fee-structure edits.
Homework alerts occur on assignments/content updates and checked status changes,
not metadata resaves. Student and school IDs determine access; names/emails do not.

On a trusted workstation with Node.js 22 and Firebase CLI:

1. Enable the Blaze plan on **edunexa-portal** (Functions deployment requires it).
   Billing is a user-managed step; no billing plan was changed by this update.
2. Sign into the Firebase CLI as the project's owner (`firebase login`).
3. From the `backend` directory: `npm --prefix functions ci` then
   `npm --prefix functions test`.
4. Deploy only this codebase:
   `firebase deploy --project edunexa-portal --only functions:student-activity`.
   This must never target the original Al-Nisa project.
5. Install the updated APK and sign in as a student. Allow Android notification
   permission. Sign in again if the student was already signed in during deployment.
6. On a second phone, save Present/Absent attendance, receive a partial/full fee
   payment, assign homework and check its status. Verify the linked student gets
   one correct in-app notice and phone alert with the app in foreground/background;
   tapping the alert must open Notifications. Also check remarks-only resaves,
   payment retries and homework metadata updates produce no duplicate alerts.
7. Verify another student/school and disabled/inactive accounts receive no alerts.
   Test an offline phone reconnecting, token rotation and switching student accounts.

FCM carries only school, notice and target UID identifiers. A WorkManager job
verifies current Firebase authorization before displaying private notice content.
App sign-out or Android force-stop can prevent delivery; saved in-app notices
remain available on the student's next authorized visit. Phone notification
permission and internet access are required for status-bar alerts.

Backend token/delivery records are server-only under the existing deny-by-default
rules. No additional client access to those collections is needed. Device tokens
are uniquely bound to the current student's authenticated UID, and invalid tokens
are removed on send errors. Use operational retention for old pushDeliveries and
stale pushDevices when deploying at scale.

