# EduNexa 0.3.0 — Workflows, polish and QR fee lookup

Native Android · `pk.edunexa.app` · versionCode 13 · Firebase `edunexa-portal`.
Install over the existing EduNexa app. Al-Nisa and Maktab apps/projects are unchanged.

## Fees from the same student QR

1. **Fees → Scan Student Card • Open Fees** dabao.
2. Wohi existing attendance/student QR scan karo. Card dobara print nahi karna.
3. Student ke saved bills khulenge. Name, registration number, session, charge aur outstanding amount check karo.
4. **Receive Fee** mein amount/method enter karke **Confirm & Receive** karo.
5. Payment save hone ke baad receipt available hogi.

Scan only looks up the student. It does not mark attendance, create a bill, receive money, trigger an online payment or debit a bank account. Fees here records money collected by the school; no payment-gateway integration was added. Other-school, missing, removed and inactive students' QR lookup is rejected. Removed profiles' real historical bills remain in Archived / Old Dues.

## What's improved

| Area | Behavior / location |
| --- | --- |
| Admin dashboard | Attendance needing attention, tuition dues / fee lookup and school setup shortcuts. Home collection listeners stop while using another module. |
| Teacher dashboard | Class, subject, homework-pending and marks-pending cards open the relevant workspace. A priority card highlights work needing review. |
| Student dashboard | Today's homework/test priorities and a direct link to the personal timeline. Existing role permissions remain. |
| Student timeline | **Students → profile → Student Timeline** for Admin; **More → Timeline** for Student. Admission, latest saved daily attendance, separate fee installments, roster-linked homework and published results across sessions. Search by text or YYYY-MM; type filters and Show more. |
| Search / loading | Student search includes guardian/emergency phone. Fee search includes guardian phone. A scoped student fee page requests only that student's profile and bills. Repeated student fee-summary calculations are cached while source data stays unchanged. |
| Admission recovery | An admission/edit draft is saved privately on the phone and partitioned by Firebase user and school. Reopen Add/Edit to recover. A draft from a changed profile is not automatically applied over the new profile. Discard saved draft is available. |
| Payment recovery | Amount, method, notes and a stable request ID survive reopening on the same phone. A retry checks the original payload if that request already exists. Refresh verifies a previously saved request and opens its real receipt. Navigation is blocked during a fee save. |
| Documents | Fee bills/receipts, student profiles, monthly reports, school reports and result gazettes use the school's logo and consistent navy/gold print elements. Print callbacks have a single-open guard. Known school IDs select the matching logo cache. |
| School onboarding | **More → School Setup** checks profile → session → classes/sections → subjects → teachers → students. Empty schools can save their first Upcoming session and then explicitly Set Current Session. Existing linked people/assignments still require an active session. |
| Owner registration | **Owner Panel → Register school** creates an empty, isolated school and links an actual Firebase Auth Admin UID. Requires a verified Owner claim and the new published rules. |

Drafts are local recovery copies, not offline submissions. Firestore remains authoritative. Text drafts are debounced briefly; a sudden process/phone failure can lose the most recent keystrokes. They are not synced to another device and are removed on successful completion. Passwords and payment-card details are never stored in these drafts; Android backup is disabled.

If a payment result is unclear, keep the request and retry the same details or return to Fees and Refresh to verify its saved receipt. Do not create a fresh payment merely because the network response was delayed. A changed-payload retry of an already processed request is rejected.

## Register a school properly

1. Publish the new `EduNexa-Firestore.rules` into **edunexa-portal → Firestore → Rules**. Existing 0.2.0 rules do not grant the new provisioning operation. The app preflights that permission before creating an Auth account.
2. Only the verified platform Owner sees Owner Panel. School Admins cannot grant themselves Owner access through a Firestore field.
3. In Register school, enter a unique lowercase school ID, real school/campus names, short form, principal/Admin name, real Admin email and a strong password (12–100 characters with uppercase, lowercase and a number).
4. Keep the credentials. After success use **Copy login** before Done; passwords are held in memory only.
5. The school Admin signs in via the regular **Admin** login and opens School Setup.
6. Add an academic session, then **Set Current Session**. Add the school's real classes/sections/subjects, then staff and students or preview an Excel import.

Registration does not add demo pupils, classes, teachers or fee rates. School metadata, Admin profile, empty academic configuration and zero registration counters are linked in one Firestore transaction. The primary Owner remains signed in while a separate Auth instance verifies the school Admin account.

Firebase Auth and Firestore are separate services. If interrupted after Auth signup, retry the **same school ID, email and password**. The verified Auth account is retained, and existing linked data is not deleted or overwritten. Another school's existing Admin account cannot be relinked. An already existing email requires its actual password; the app does not take over accounts or reset passwords.

## First-time Owner activation (still required if not done)

The claim tool remains `EduNexa/backend/functions/configure-owner.js` in the source ZIP. In a trusted local Firebase Admin environment with Application Default Credentials and the functions dependencies installed:

```bash
GOOGLE_CLOUD_PROJECT=edunexa-portal node functions/configure-owner.js ACTUAL_FIREBASE_AUTH_UID grant
```

Run from `backend`, then sign out/sign in. The UID must be an existing active Admin with a linked school. Use `revoke` instead of `grant` to remove Owner access. Keep private keys out of the APK, source and chat.

No live Firebase rules or Owner claims were changed by this build. Owner activation and new registration require those setup steps. Cloud Functions deployment or a Blaze upgrade is not required for these features. Normal Firebase usage and free-plan quotas still apply.

## Retained 0.2.0 behavior

- QR attendance remains Admin-only with multiple scanning. Existing marked statuses are preserved.
- School Settings → Attendance Policy configures automatic Late. Arrival classification uses the staff phone's Pakistan time; keep automatic date/time enabled. A server timestamp is also saved.
- Activity History starts from 0.2.0, keeps the latest 100 changes per record and displays the 250 most recently edited source records per module. It is operational history, not an archive of deleted documents.
- Existing login passwords cannot be retrieved. Bulk login slips show portal email and reset guidance; new Excel-import credentials remain in that import's Login Report.
- In-app student activity alerts remain enabled. Background mobile push/paid Functions were not enabled by this update.

## Verification and phone checks

Clean APK build passed. 251 JVM tests and 12 Firestore emulator permission tests passed with no failures. Signing identity and package are preserved. Rules fixtures run only against `demo-edunexa-rules`.

Still verify on your real phones:

1. Scan an existing card from Fees; correct pupil/bills open, no money/attendance is recorded by the scan.
2. Record a real partial fee payment, Refresh and verify one receipt and the correct balance. Retrying its request must not duplicate it.
3. Close/reopen a partly filled admission to check recovery. Review before saving; no fake test pupil should be added to production.
4. Open Admin/Teacher/Student priorities, personal timeline and a few PDF previews at your usual font size.
5. After Owner setup, register an agreed real school, keep its Admin credentials, sign in as that Admin and complete the empty-school checklist.
6. Check that another school's QR/data stays inaccessible.

Camera scanning, Android print preview, actual Auth signup/sign-in and live registration were not exercised on a physical phone in this build environment. Emulator tests verify Firestore permissions; they do not replace live Auth/device verification.
