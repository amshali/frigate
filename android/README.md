# Frigate for Android

This is a lightweight Android WebView wrapper for Frigate. It opens the
existing Frigate web interface, so web features and authentication are
provided by the server rather than reimplemented in the app.

## Build and run

Open this `android` directory in Android Studio, let Gradle sync, then run the
`app` configuration on a device or emulator. The project requires JDK 17 and
Android SDK platform 36.

To build and run the unit tests from this directory:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Open Frigate

Enter the Frigate server URL, including its scheme and optional port, such as
`https://frigate.example` or `http://192.168.1.20:5000`. Reverse-proxy paths
are supported. Log in through Frigate's web interface if authentication is
enabled. The server URL is saved on the device; login cookies are managed by
Android WebView.

The app permits HTTP connections to support local Frigate installations. Use
HTTPS when connecting over an untrusted network. JavaScript and DOM storage
are enabled for the Frigate interface; local file access is disabled.

The Android back button navigates within the web interface when possible.

## Release builds

1. Create a signing key once and keep it (and its passwords) safe and backed up. Losing it means you cannot update the installed app:

   ```bash
   keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 \
     -validity 10000 -alias frigate
   ```

2. Copy `keystore.properties.example` to `keystore.properties` and fill in the values. Both files are git-ignored.
3. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts` for every release.
4. Build:

   ```bash
   ./gradlew --no-daemon assembleRelease   # app/build/outputs/apk/release/app-release.apk
   ./gradlew --no-daemon bundleRelease     # app/build/outputs/bundle/release/app-release.aab (Play Store)
   ```

Without `keystore.properties` the release APK is built unsigned and cannot be installed.
