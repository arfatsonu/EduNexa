# EduNexa 0.2.0 — Combined update

Package: `pk.edunexa.app` · Firebase: `edunexa-portal` · versionCode: 12.
This is the separate native EduNexa app. Al-Nisa and Maktab projects are unchanged.

## Install

Install the APK over the existing EduNexa app. Uninstall karne ki zaroorat nahi.
Existing QR cards work; multiple scanning is preserved.

## Seven features

| Feature | Kahan milega | Kya karna hai |
| --- | --- | --- |
| Arrival time + automatic Late | School Settings → Attendance Policy → Edit | Automatic Late enable karo, start time `08:00` jaisa HH:mm mein likho, grace minutes set karke Save karo. Attendance card scan arrival time aur Present/Late save karega. |
| Absent follow-up | Attendance → Absent follow-up | Selected date ke saved absent students nazar aayenge. Student/class kholo, Call, WhatsApp ya saved reason use karo. |
| Excel admission | More → Excel Import; School Settings → Import Data bhi available | Template download karo, data fill karo, upload ka preview check karo. Errors/duplicate rolls theek karke import karo. Har batch mein 100 people tak. Registration automatically reserve hoti hai; guessed registration numbers nahi bante. |
| Monthly student report | Students → student profile → Complete Student Report → Filters | Session aur month select karo. Monthly results ke liye Selected Month choose karo. Print / Save PDF mein logo, attendance, homework/teacher checks, results, selected-month fee bills aur saved profile remarks hain. |
| Bulk cards / login slips | More → Cards & Login Slips | Session, class, section select karo. QR cards ya Login slips choose karke Print / Save PDF dabao. Up to 100 active/on-leave students per batch. |
| Activity history | More → Activity History | Attendance changes, real fee payments, student/teacher profile changes/removal aur school settings ki new activity dekho. Search aur Refresh available. |
| Owner panel | Verified Owner ke Admin menu → Owner Panel | All registered schools ka profile, active session/class setup aur access status dekho. Reason aur confirmation ke saath doosre school ka portal suspend/enable karo; records delete nahi hote. |

## Important behavior

- QR scan online Firebase transaction acknowledge hone ke baad hi saved message deta hai. Already marked Present/Absent/Leave/Late ko dobara scan overwrite nahi karta.
- Late is strictly after start time + grace. Exactly cutoff time par Present. Grace range 0–180 minutes; the cutoff cannot cross midnight.
- Arrival classification uses the staff phone clock in Asia/Karachi; keep automatic date/time enabled. A Firebase server timestamp is also saved as `cloudArrivedAt`. This is not a tamper-proof hardware timeclock.
- Manual attendance can still record Present/Absent/Leave/Late; arrival time is only added by card scans.
- Unsaved attendance is Unmarked, never automatically Absent. No automatic mass-absence or parent message sending was added.
- Existing passwords are not retrievable. Bulk login slips show saved portal emails and password/reset guidance. New Excel-import passwords remain available in that import's Login Report; save it before leaving.
- The Admin monthly report uses real records. Monthly fee paid/due values belong to the selected month's bills and may include installments paid later against those bills. This is not a cash-received-by-date ledger.
- Activity starts from this version onward. History keeps the latest 100 changes per record and shows the 250 most recently edited source records per module. It records action, actor name/UID, time and changed field names; no passwords or old field values. Updated rules prevent rewriting retained history or forging another actor UID. This is an operational history, not an immutable archive of deleted documents.
- Existing student login emails keep their current values. Login slips do not rename accounts.

## One-time Firebase setup for Owner access

Normal attendance/import/printing features can run using the existing school rules. Owner listing/access controls and the new history protections require the supplied rules to be published.

1. Firebase console mein **edunexa-portal** project confirm karo.
2. Firestore Database → Rules mein `EduNexa-Firestore.rules` ka full content paste karke **Publish** karo. It is also in the source ZIP at `EduNexa/backend/firestore.rules`.
3. Owner permission must be a Firebase Auth **custom claim**, not a Firestore field. The prepared tool is `EduNexa/backend/functions/configure-owner.js`. It requires a trusted local environment with Firebase Admin credentials authorized for edunexa-portal, Node 22+, and the existing functions dependencies (`npm ci` in `backend/functions`). Do not share private keys/passwords in chat or bundle them in the APK.
4. With Application Default Credentials configured locally, run from `backend`:

```bash
GOOGLE_CLOUD_PROJECT=edunexa-portal node functions/configure-owner.js ACTUAL_FIREBASE_AUTH_UID grant
```

The UID must belong to an existing active Admin account linked to a school. The tool preserves other claims, refuses another project, and verifies the profile before granting.

5. Sign out and sign in again. **Owner Panel** appears only for that verified Owner.
6. Revoke if needed using the same command with `revoke`. It also revokes refresh tokens. The linked school cannot be suspended from its own Owner session.

No Cloud Functions deployment or Blaze upgrade is required for this Owner setup. Rules and Owner claim have **not been changed on the live project** by this update.

## Verification

- Clean Android APK build: passed.
- 238 JVM tests: passed, no failures/errors.
- 8 Firestore emulator rule tests: passed, including allowed teacher edits/history retention, Admin fees/profiles, Owner claim boundaries, school suspend/restore and cross-school denial.
- Existing update signing certificate: preserved.
- Campus/project isolation check: passed.
- Camera scanning, printing on your phone, real Firebase writes and live Owner activation still require device/live verification. Emulator tests use a separate `demo-edunexa-rules` project only.

## Quick phone checks

1. Start time/grace set karo. Do existing QR cards scan karo; already-marked status preserve hona chahiye. New unmarked student par arrival time aur correct Present/Late check karo.
2. Mark one real absence, reason save karo, page reload karke reason check karo. Call/WhatsApp buttons only open your phone apps; no automatic message is sent.
3. Excel template ka small batch preview karo; invalid or duplicate roll ko block hona chahiye. Production mein test ke liye fake pupils mat add karo.
4. One student ka monthly report aur one class ke cards/login slips Print / Save PDF preview mein check karo.
5. A real attendance/fee/profile action ke baad Activity History Refresh karo.
6. Owner setup complete hone ke baad only an agreed test school's suspend/restore flow check karo; student/teacher access must block while suspended and return after enable.
