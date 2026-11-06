# EduNexa 0.3.4 test candidate

The phone displayed cached Al-Noor branding, then briefly opened the Owner
workspace before closing. No device stack trace was available.

Startup, signed-out and Owner sessions now use neutral EduNexa branding. Only
a verified school session may load its own cached branding, attach its school
listener, or change launcher aliases and pinned shortcuts. Composition remains
stable through session restoration. Package, signing key and Firebase project
are unchanged; versionCode is 17.

Debug builds retain a local report with exception types and stack locations,
excluding exception messages. On the next launch the user can copy the report
and continue. Nothing is sent automatically; Android's normal crash handling
is preserved. The launcher interaction is a candidate explanation for closing;
the exact phone cause still requires the updated APK test or a stack trace.

Regression tests cover neutral startup/Owner branding, school isolation and
diagnostic message privacy. assembleDebug and testDebugUnitTest passed: 265 tests, zero failures, errors
or skips. APK signing key matches 0.3.3. Phone verification remains pending.

Install over 0.3.3 without uninstalling or clearing data. Open the saved Owner
session: loading should show EduNexa, followed by the Owner school list. If it
closes, reopen and copy the previous-error report. Then test Logout and school
Admin login, which must open only school-001. This APK changes no Firebase data
or rules.
