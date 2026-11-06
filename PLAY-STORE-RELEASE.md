# EduNexa release preparation — 20 October 2026

## Verified in this source

- App: EduNexa 0.3.2 test candidate, versionCode 15.
- Package: pk.edunexa.app; compile/target SDK 36; minimum SDK 24.
- Firebase configuration identifies edunexa-portal.
- Developer credit: Muhammad Arfat.
- The source isolation script passes. This is not a live Firebase/device test.

## Changes made for release preparation

- Replaced the empty Unix gradlew file with a working Bash wrapper launcher.
- Added optional upload signing from private Gradle properties or environment variables.
- Explicitly disabled debugging in the release variant.
- Added ignores for local build output and private signing files.
- Kept the current app version, package, UI, Firebase configuration and debug signing identity.

No release keystore has been generated. The bundled campus-debug keystore is for development only; do not use it for Play upload.

## Build status

No 0.3.2 APK/AAB was produced here. The full Android Gradle build stopped at the wrapper download because Java could not reach the distribution server. Ten targeted Kotlin/JUnit regression tests passed, and the native camera screen compiled separately against Android/ZXing APIs. See UPDATE-0.3.2.md for the exact validation scope and device acceptance steps. The previously built 0.3.1 APK/AAB does not contain these source fixes.

The connected GitHub a2-campus branch inspected in arfatsonu/al-nisa-android still identifies the older pk.a2.campus 0.1.1 app. Do not build that branch as if it were this EduNexa source. No remote repository or Firebase data was changed.

## Build from Android Studio on a computer

1. Extract the ZIP and open the EduNexa folder in Android Studio.
2. Allow Gradle sync and install required Android SDK packages. Use a current JDK compatible with the project's existing AGP/Gradle versions.
3. Run the existing unit tests and isolation script before release.
4. Choose Build → Generate Signed Bundle / APK → Android App Bundle.
5. Select an existing EduNexa upload key if one exists. Otherwise create a new private upload keystore for this first Play release. Keep the keystore and passwords backed up securely, outside the source ZIP and chat.
6. Select the release variant and generate the bundle. Never upload a debug-signed bundle.
7. Inspect the resulting AAB's package, version and signature. Confirm the build is not debuggable and run device/reviewer-login checks.
8. In Play Console configure Play App Signing, then use the signed AAB for testing after account registration is available.

## Optional command-line signing

Put all four settings in your PRIVATE user Gradle properties file (not the project's tracked gradle.properties), or provide them as environment variables:

```properties
EDUNEXA_UPLOAD_STORE_FILE=/absolute/path/to/private-upload.jks
EDUNEXA_UPLOAD_STORE_PASSWORD=YOUR_PRIVATE_STORE_PASSWORD
EDUNEXA_UPLOAD_KEY_ALIAS=YOUR_UPLOAD_ALIAS
EDUNEXA_UPLOAD_KEY_PASSWORD=YOUR_PRIVATE_KEY_PASSWORD
```

Run:

```bash
python3 scripts/verify-campus-isolation.py
./gradlew --no-daemon testReleaseUnitTest bundleRelease
```

Expected output after a successful build: app/build/outputs/bundle/release/app-release.aab.
With all signing settings unset, the release bundle is UNSIGNED and is not upload-ready. Partial signing settings fail configuration. No signing password or key is included here.

For wrapper JVM flags containing spaces or complex quoting, use the private Gradle properties file; the compact Bash launcher splits JAVA_OPTS and GRADLE_OPTS on whitespace.

## Before public launch

- Complete identity/account registration and the store listing.
- Confirm privacy policy, actual Data safety disclosures, intended age groups and account deletion obligations against the final app.
- Provide authorized review logins; do not create test records inside a real school without approval.
- Verify real-device role access and school isolation. No device verification was performed here.
- New personal accounts require 12 continuously opted-in closed testers for 14 days before applying for production access.
- Register the Play app-signing certificate with Firebase/API providers where required. Upload and app-signing certificates may differ.
- A Play-signed build may not install over an existing debug-signed APK. Preserve login access and verify server data before any reinstall.

Official signing guide: https://developer.android.com/studio/publish/app-signing
Testing requirements: https://support.google.com/googleplay/android-developer/answer/14151465?hl=en
