---
applyTo: '**'
---

# VLC Android Development Instructions

## Project Context
This is the VLC for Android repository - the official Android port of VLC media player.
The current branch is `codex/add-audio-metadata-recognition-for-videos`, which suggests work on audio metadata recognition for video files.

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

## Reference Documentation
See `CODEBASE_OVERVIEW.md` for comprehensive module documentation.
