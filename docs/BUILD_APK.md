# Building VLC for Android APKs

The project already ships with Gradle build types that can emit installable APKs. You can generate unsigned debug builds for local testing or create a signed release when you provide a keystore.

## Prerequisites
- Android SDK and NDK installed and referenced through `ANDROID_SDK` and `ANDROID_NDK` environment variables.
- Java 11+ available on the PATH.
- Gradle wrapper scripts executable (`chmod +x gradlew`).

## Build a debug APK
This variant uses the default debug keystore that ships with the Android SDK.

```bash
./gradlew :application:assembleDebug
```

Debug APKs are written to `application/app/build/outputs/apk/debug/`.

## Build a signed release APK
1. Create or reuse a keystore (for example, `~/.android/release.keystore`).
2. Add the following properties to `gradle.properties` (either in the repository root or in `~/.gradle/gradle.properties`):
   ```properties
   keyStoreFile=/absolute/path/to/release.keystore
   storealias=<your-key-alias>
   storepwd=<keystore-password>
   ```
   Alternatively, you can provide the password via the `PASSWORD_KEYSTORE` environment variable when invoking Gradle.
3. Run the signed release build:
   ```bash
   ./gradlew :application:assembleSignedRelease
   ```

Signed APKs are written to `application/app/build/outputs/apk/signedRelease/`.

## Build an App Bundle
If you need an Android App Bundle instead of an APK, use the existing bundle build type:

```bash
./gradlew :application:bundleRelease
```

The output is generated under `application/app/build/outputs/bundle/release/`.
