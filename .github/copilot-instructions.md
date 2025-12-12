---
applyTo: '**'
---

# VLC Android Development Instructions

## Project Context
This is the VLC for Android repository - the official Android port of VLC media player.
The current branch is `dsp-new-fix`, which is focused on DSP audio processing integration.

## RootlessJamesDSP Integration Reference

This project aims to work compatibly with **RootlessJamesDSP** - a system-wide JamesDSP audio processing engine for non-rooted Android devices.

**Repository:** https://github.com/timschneeb/RootlessJamesDSP

### How RootlessJamesDSP Works

RootlessJamesDSP uses Android's internal audio capture (MediaProjection API) to intercept and process audio streams. Unlike traditional audio effect apps that rely on Android's built-in `AudioEffect` API, RootlessJamesDSP gains full access to audio streams for custom DSP processing.

### Key Integration Points

1. **Audio Session Broadcasts:**
   - RootlessJamesDSP listens for `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` and `AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION`
   - VLC should broadcast these intents with:
     - `AudioEffect.EXTRA_AUDIO_SESSION` - The audio session ID
     - `AudioEffect.EXTRA_PACKAGE_NAME` - The package name ("org.videolan.vlc")

2. **Compatibility Requirements:**
   - Apps must NOT block internal audio capture (no `ALLOW_CAPTURE_BY_NONE` flag)
   - Apps should use standard Android audio playback APIs (not AAudio native C++ API exclusively)
   - VLC uses LibVLC which should be compatible since it outputs through Android's audio system

3. **RootlessJamesDSP Modes:**
   - **Rootless Mode:** Uses MediaProjection to capture system audio (Android 10+, requires Shizuku/ADB)
   - **Root Mode:** Uses AudioEffect framework directly with Magisk module
   - VLC should work with both modes if audio session intents are properly broadcast

4. **Apps Confirmed Working with RootlessJamesDSP:**
   - YouTube, YouTube Music, Amazon Music, Deezer, Poweramp, Twitch
   - VLC should follow similar patterns to ensure compatibility

### When Modifying Audio Playback

Ensure any audio playback changes:
1. Properly acquire and release audio sessions
2. Broadcast audio session open/close intents
3. Don't set audio capture restriction flags that would block RootlessJamesDSP
4. Use Android's standard audio APIs rather than bypassing them

### JamesDSP Audio Effects Supported

- Limiter control
- Output gain control  
- Dynamic range compressor
- Bass boost
- FIR equalizer
- Graphic EQ (Arbitrary response equalizer)
- ViPER-DDC
- Convolver (impulse response)
- Live-programmable DSP (scripting engine)
- Analog modeling
- Soundstage widener
- Crossfeed
- Virtual room reverb

## Repository Structure Quick Reference

### Core Directories
- `application/vlc-android/` - Main VLC Android application code
- `application/television/` - Android TV specific UI (Leanback)
- `medialibrary/` - Native medialibrary with JNI bindings
- `application/resources/` - Shared resources and constants
- `application/tools/` - Utility libraries

### Key Source Locations
- **Playback Logic:** `application/vlc-android/src/org/videolan/vlc/`
  - `PlaybackService.kt` - Central playback service
  - `media/PlayerController.kt` - Player control logic
  - `media/PlaylistManager.kt` - Playlist management
  
- **Media Data Models:** `medialibrary/src/org/videolan/medialibrary/`
  - `interfaces/media/MediaWrapper.java` - Core media item (title, artist, album, etc.)
  - `interfaces/Medialibrary.java` - Library interface
  
- **Video UI:** `application/vlc-android/src/org/videolan/vlc/gui/video/`
  - `VideoGridFragment.kt` - Video listing
  
- **Audio UI:** `application/vlc-android/src/org/videolan/vlc/gui/audio/`
  - `AudioBrowserFragment.kt` - Audio library browser
  
- **Providers:** `application/vlc-android/src/org/videolan/vlc/providers/`
  - `medialibrary/VideosProvider.kt` - Video data provider
  - `medialibrary/TracksProvider.kt` - Audio tracks provider

### Build Commands
```bash
# Debug build
gradle :application:app:assembleDebug

# Signed release
gradle :application:app:assembleSignedRelease
```

## Development Guidelines

### When Adding Features
1. Check if the feature affects MediaWrapper - that's where media metadata lives
2. For UI changes, identify if it's mobile (`vlc-android`) or TV (`television`)
3. Follow MVVM pattern: Model → Repository/Provider → ViewModel → Fragment/Activity
4. Use Kotlin coroutines with appropriate dispatchers (IO for disk, Main for UI)

### When Modifying Media Handling
1. MediaWrapper in `medialibrary` holds all media metadata
2. PlaybackService is the central point for playback control
3. PlayerController wraps the LibVLC MediaPlayer
4. Changes to native code require rebuilding medialibrary JNI

### Key Classes for Audio Metadata
- `MediaWrapper.java` - Contains fields like `mArtistName`, `mAlbumName`, `mGenre`, `mTrackNumber`
- Metadata constants: `META_AUDIOTRACK`, `META_GAIN`, etc.
- The medialibrary parses and indexes audio metadata during scanning

### File Organization Pattern
```
feature/
├── FeatureFragment.kt (or Activity)
├── FeatureAdapter.kt (for lists)
├── FeatureViewModel.kt (in viewmodels/)
├── FeatureProvider.kt (in providers/)
└── FeatureRepository.kt (in repository/ if persistence needed)
```

### Testing
- Unit tests: `*/src/test/`
- Instrumented tests: `*/src/androidTest/`
- Run with: `gradle test` or `gradle connectedAndroidTest`
- **Note:** Testing and building require a proper environment (Gradle 8.14.3, JDK 17, Android SDK). Use the provided Docker environment in `docker-build/` if local setup is missing.

### Building with Docker (Recommended)
A Docker-based build environment is provided to ensure consistent builds without local setup issues.

**Quick Build:**
```bash
# Run the convenience script from project root
chmod +x build_in_docker.sh && ./build_in_docker.sh
```

**Manual Docker Commands:**
```bash
# Start the build container
cd docker-build
docker-compose up -d

# Run a debug build
docker-compose exec vlc-builder gradle :application:app:assembleDebug

# Run a signed release build (requires signing keys)
docker-compose exec vlc-builder gradle :application:app:assembleSignedRelease

# Access the container shell for other commands
docker-compose exec vlc-builder bash

# Stop the container when done
docker-compose down
```

**Build Artifacts:**
- Debug APK: `application/app/build/outputs/apk/debug/VLC-Android-*-debug-all.apk`
- Signed APK: `application/app/build/outputs/apk/signedRelease/`

**Note:** The Docker build uses the official VideoLAN Android build image, which includes all necessary dependencies (Android SDK, NDK, etc.).

## Files to Avoid Committing
- `*.keystore` - Signing keys
- `keystore-base64.txt` - Encoded keystore
- `feedback.md` - Working file for AI interaction
- `CODEBASE_OVERVIEW.md` - Documentation file (check if needed)
- `local.properties` - Local SDK paths

## Common Patterns in This Codebase

### LiveData Usage
```kotlin
val items = MutableLiveData<List<MediaWrapper>>()
items.postValue(newList) // From background thread
items.value = newList    // From main thread
```

### Coroutine Scopes
```kotlin
lifecycleScope.launch { } // In Activity/Fragment
viewModelScope.launch { } // In ViewModel
withContext(Dispatchers.IO) { } // For I/O operations
```

### Media Type Checking
```kotlin
when (media.type) {
    MediaWrapper.TYPE_VIDEO -> // Video handling
    MediaWrapper.TYPE_AUDIO -> // Audio handling
    MediaWrapper.TYPE_DIR -> // Directory
}
```

## VLC3 vs VLC4
- VLC3 (default): minSdk 17, libvlcVersion '3.6.5'
- VLC4: minSdk 21, libvlcVersion '4.0.0-eap23'
- Use `forceVlc4=true` gradle property to switch

## Audio Session Management for DSP Compatibility

When working with audio playback, ensure proper audio session handling for external DSP apps like RootlessJamesDSP:

```kotlin
// When starting playback - broadcast session open
val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioManager.STREAM_MUSIC)
context.sendBroadcast(intent)

// When stopping playback - broadcast session close
val closeIntent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
closeIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
closeIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
context.sendBroadcast(closeIntent)
```

## Reference Documentation
See `CODEBASE_OVERVIEW.md` for comprehensive module documentation.
