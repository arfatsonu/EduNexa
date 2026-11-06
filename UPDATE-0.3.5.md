# EduNexa 0.3.5 Owner crash fix

The device report confirmed StackOverflowError at OwnerPanelScreen.kt:61.
The file-local Any?.orEmpty extension called itself when converting school
card text. Removed that extension and routed school name, campus and active
session name through the named, nonrecursive OwnerPolicy.displayText function.
Missing fields display an empty string or the existing fallback label.

Regression coverage exercises populated school fields, missing values, empty
strings, numeric session names and the missing-session fallback. The source
check rejects reintroducing the broad orEmpty extension in OwnerPanelScreen.

Version code 18; package, signing key and Firebase project unchanged. Retains
0.3.4 neutral startup/Owner branding and local debug crash diagnostics. No
Firebase records or rules are changed by this update.

Install over the existing APK without uninstalling or clearing data. Open the
Owner account and check that the school list finishes loading. Then check the
school Admin login opens only its school. Real-device verification is pending.

Validation: clean assembleDebug and testDebugUnitTest succeeded; 266 tests in
50 suites, zero failures, errors or skips. Isolation and Owner source checks
passed. Packaged APK reports version 0.3.5 / code 18; signature matches 0.3.4.
