# EduNexa 0.3.6 — Owner school management

The Owner registry now has school/enabled/suspended totals, searchable school
cards, status filters, logo previews and separate Edit profile / Manage Admin
actions. The mobile list scrolls as one surface; forms use a full-screen editor
with keyboard insets and discard confirmation for unsaved profile changes.

## School identity

Owner can edit school name, campus, short form, address, official email, phone,
WhatsApp, principal, tagline and affiliation, and choose/remove a school logo.
Images are resized and converted to bounded PNG data using the existing logo
pipeline; no Storage deployment or billing upgrade is needed. The school ID is
permanent. Existing student/teacher login IDs do not change with a short form edit.

Saves validate input, write only changed profile fields and preserve unrelated
settings and school data. Transactions reject a concurrent edit to the same
field and record the Owner UID and server update timestamp. Existing Admin
settings, academic data, access controls and role links are preserved.

## Admin login management

Owner can select an existing Admin, edit their name, request a reset email,
change a known password, and request a verified login-email change. Password
changes require the current Admin credentials and a matching confirmation.
Forgotten passwords use the email-reset link without the current password.

Credential verification uses an isolated secondary Firebase Auth instance,
checks the exact selected UID and school, rejects platform Owner accounts,
rechecks active Admin status, and always signs out/deletes the secondary instance.
The primary Owner login stays signed in. Passwords are not stored in Firestore,
saved instance state, logs, source or school profiles.

For an email change: verify current Admin credentials, request a link at the
new email address, complete that link, then enter the new login email with the
current password and choose Verify login & sync saved email. Only the email
returned by a verified sign-in for that same Admin UID is saved. A Firestore
email edit alone never changes Firebase Authentication credentials. If both
the inbox and password are inaccessible, use Firebase Authentication recovery
while preserving the original UID and school records.

## Deploy and test

Version 0.3.6 / code 19, package pk.edunexa.app, Firebase edunexa-portal and the
update signing certificate are retained. The 0.3.5 recursion fix, neutral Owner
branding and debug crash-report privacy remain in place.

Publish the matching Firestore rules before testing profile saves or Admin
metadata management. Until then, the APK explains missing edit permission.
The rules add narrowly scoped Owner identity edits and limited Admin-only
metadata queries. They retain student/teacher cross-school restrictions and
deny school Admin forgery of the new Owner audit fields. No Functions or Storage
rules deployment is required. Live rules have NOT been published from this workspace.

Install the APK over the current app without uninstalling or clearing data.
Test with a school you control:

1. Open Owner, search/filter schools and inspect the new cards.
2. Edit a school address/logo; save, refresh and reopen to confirm persistence.
3. Open that school's Admin portal; confirm its identity updates and records remain.
4. Edit Admin name; confirm only the selected school's Admin changes.
5. Request a password reset, complete it and test Admin sign-in.
6. If needed, test the email verification/sync sequence described above.
7. Return to Owner; check a different school remains unchanged.

The redesigned UI and live Auth email/password flows still require phone testing.

Validation completed: clean Android build; 273 unit tests in 51 suites, zero
failures/errors/skips; 17 Firestore emulator tests passed; package isolation and
Owner recursion checks passed. APK metadata is 0.3.6/code 19/API 36 and its signing
certificate matches the existing APK. Device UI/Auth checks remain pending.
