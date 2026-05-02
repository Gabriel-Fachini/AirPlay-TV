---
inclusion: auto
---

# Technical Stack

## Languages & Frameworks

- **Primary language**: Kotlin
- **Native code**: C/C++ via Android NDK (for AirPlay library integration)
- **JNI bridge**: Kotlin ↔ C/C++ communication
- **Build system**: Gradle (Kotlin DSL)
- **IDE**: Android Studio

## Android APIs

- **Minimum API**: 28 (Android 9)
- **Target platform**: Android TV
- **Architecture**: MVVM (simple, no DI frameworks)
- **Async**: Kotlin Coroutines (kotlinx.coroutines)
- **Network discovery**: NsdManager (mDNS)
- **Video decoding**: MediaCodec (H.264 hardware acceleration)
- **Video rendering**: SurfaceView
- **Audio decoding**: MediaCodec (AAC)
- **Audio playback**: AudioTrack

## External Libraries

- **AirPlay protocol**: Open source library (RPiPlay or UxPlay) compiled via NDK
- **Logging**: Android Log (or Timber if needed)
- **UI**: AndroidX Leanback (Android TV components)

## Key Technologies

### Network
- **Service discovery**: mDNS via NsdManager
- **Protocol**: RTSP (Real Time Streaming Protocol)
- **Transport**: RTP over UDP
- **Service type**: `_airplay._tcp`

### Media Pipeline
- **Video codec**: H.264 (AVC)
- **Audio codec**: AAC-LC
- **Synchronization**: RTP timestamps
- **Buffering**: 3-5 frame jitter buffer (100-150ms @ 30fps)

### Threading
- **Network reception**: Dedicated thread (ProtocolHandler)
- **Video decoding**: Dedicated thread (VideoDecoder)
- **Audio decoding**: Dedicated thread (AudioDecoder)
- **Coordination**: Coroutines with Dispatchers.IO

## Common Commands

### Build & Install

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on TV via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Quick reinstall
adb install -r app-debug.apk
```

### Development

```bash
# Connect to TV over network
adb connect 192.168.1.100:5555

# Check connected devices
adb devices

# View logs (filtered)
adb logcat | grep "AirPlay"

# Clear app data
adb shell pm clear com.airplay.tv

# Uninstall app
adb uninstall com.airplay.tv
```

### Debugging

```bash
# View all logs from app
adb logcat | grep "com.airplay.tv"

# View specific component logs
adb logcat | grep "AirPlay:Video"
adb logcat | grep "AirPlay:Audio"
adb logcat | grep "AirPlay:Protocol"

# Clear logcat buffer
adb logcat -c

# Monitor CPU/memory usage
adb shell top | grep com.airplay.tv

# Dump system info
adb shell dumpsys media.codec
```

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device)
./gradlew connectedAndroidTest

# Generate test coverage report
./gradlew jacocoTestReport
```

## Project Structure

```
app/src/main/
├── java/com/airplay/tv/
│   ├── MainActivity.kt
│   ├── ui/                    # ViewModels, UI State
│   ├── service/               # AirPlayService, SessionManager
│   ├── network/               # mDNSModule
│   ├── protocol/              # ProtocolHandler, RTPParser
│   ├── media/                 # VideoDecoder, AudioDecoder
│   └── util/                  # Logger, Constants
├── cpp/                       # Native code (NDK)
│   ├── CMakeLists.txt
│   ├── airplay-lib/           # Open source AirPlay library
│   └── jni-bridge.cpp         # JNI wrapper
├── res/                       # Resources (layouts, strings)
└── AndroidManifest.xml
```

## Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Android core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.leanback:leanback:1.0.0")
    
    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
```

## NDK Configuration

```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

## Performance Targets

- **Latency**: < 1000ms (ideal: 300-500ms)
- **FPS**: > 24 (ideal: 30)
- **CPU usage**: < 80%
- **Memory usage**: < 512MB
- **Connection success rate**: > 90%
- **A/V desync**: < 100ms

## Log Tags

Use these standardized tags for logging:

```kotlin
const val TAG_MDNS = "AirPlay:mDNS"
const val TAG_PROTOCOL = "AirPlay:Protocol"
const val TAG_VIDEO = "AirPlay:Video"
const val TAG_AUDIO = "AirPlay:Audio"
const val TAG_SESSION = "AirPlay:Session"
```
