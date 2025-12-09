# Building VLC for Android APKs

The project ships with Gradle build types that can emit installable APKs. You can generate unsigned debug builds for local testing or create a signed release when you provide a keystore.

## Prerequisites
- Android SDK and NDK installed and referenced through `ANDROID_SDK` and `ANDROID_NDK` environment variables. Accept licenses with `yes | sdkmanager --licenses` after installing the command line tools.
- Java 17+ available on the PATH (the GitHub Action uses Temurin 17).
- Gradle 8.14.3 installed locally (the repository does not bundle wrapper binaries). Verify your installation with:
  ```bash
  gradle --version
  ```

## Build a debug APK
This variant uses the default debug keystore that ships with the Android SDK.

```bash
gradle :application:assembleDebug
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
   gradle :application:assembleSignedRelease
   ```

Signed APKs are written to `application/app/build/outputs/apk/signedRelease/`.

## Build an App Bundle
If you need an Android App Bundle instead of an APK, use the existing bundle build type:

```bash
gradle :application:bundleRelease
```

The output is generated under `application/app/build/outputs/bundle/release/`.

## Build a release APK in CI
The repository ships with a reusable GitHub Action workflow in `.github/workflows/android-release.yml`. It:
- checks out the sources
- installs Android SDK platform 35, build tools 35.0.0, platform tools, and NDK 27.1.12297006 via `sdkmanager --channel=3`
- runs Gradle to produce `:application:assembleRelease`
- uploads the resulting APKs from `application/app/build/outputs/apk/release/` as workflow artifacts

Trigger the workflow manually from the Actions tab or run it on pushes and pull requests to the main branches.
