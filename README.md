# Solina Feed

The feed page (swipe right on the home screen) for **Solina Launcher**.

- Jalali date and fixed solar-calendar occasions
- Weather (Open-Meteo, no account needed)
- Daily tasks and a quick note (stored only on the device)
- News from RSS/Atom sources you choose (BBC Persian and Google News by default)

The app talks to the launcher through the standard launcher-overlay AIDL interface
(`com.android.launcher3.WINDOW_OVERLAY`). Only Solina Launcher packages may drive it.

## Build

GitHub Actions builds the APK on every push to `main` (Actions tab, Artifacts, `SolinaFeed`).
Locally: JDK 21, Android SDK, then `./gradlew assembleRelease`.

## Signing key

Release builds are signed with a private key kept **outside** the repository, in these repository secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Without them the build falls back to the Android debug key. Solina Launcher only accepts a feed
whose signature matches the key hash built into it, so keep a safe backup of the private key.

## Notice

`app/src/main/aidl/**` holds the launcher-overlay interface definitions, copied unchanged from the
Solina Launcher repository (`lawnchair/aidl/com/google/android/libraries/launcherclient`) so both sides
speak the same protocol. See the `NOTICE.md` of that repository for their origin and license.
