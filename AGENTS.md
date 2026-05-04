# AirPlay TV MVP — Agent Rules

## Project

- AirPlay mirroring receiver for Android TV (Sony KD-55X755F, API 28, ~7 years old)
- Personal/home use, single-session, no PIN authentication
- Stack: Kotlin + C++/NDK, MVVM simple, Coroutines
- Source: `AirPlayTV/app/src/main/java/com/airplay/tv/`
- Native: `AirPlayTV/app/src/main/cpp/`

## Build & Run

```bash
cd AirPlayTV && ./gradlew assembleDebug                    # Build
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Install
adb logcat | grep "AirPlay"                                 # Logs
cd AirPlayTV && ./gradlew test                              # Unit tests
```

## Architecture

- Layers: `ui/` → `service/` → `protocol/` + `network/` + `media/`
- UI states: `Idle → Connecting → Mirroring → Idle` (or `→ Error → Idle`)
- Video: mirroring TCP via MirrorServer → AirPlayMirroringSession → VideoDecoder → SurfaceView
- Audio: RTP UDP via RTPReceiver → AudioDecoder → AudioTrack
- Native C++ modules: `protocol/` (RTSP, FairPlay), `network/` (RTP, Mirror, NTP)

## Immutable Decisions (DO NOT CHANGE without asking)

- NsdManager for mDNS (not jmdns)
- MediaCodec H.264 + SurfaceView (not ExoPlayer)
- MediaCodec AAC + AudioTrack
- MVVM simple (no Hilt/Koin/Dagger)
- Coroutines for async (not RxJava)
- Single session, no auto-reconnect
- Min API 28, target Android TV

## Code Conventions

- Log tags: `TAG_MDNS`, `TAG_PROTOCOL`, `TAG_VIDEO`, `TAG_AUDIO`, `TAG_SESSION`
- Classes: PascalCase (`AirPlayService`), Functions: camelCase (`startSession()`)
- Constants: UPPER_SNAKE_CASE in `Constants.kt` or companion objects
- Always release resources in finally/catch (MediaCodec, AudioTrack, sockets)
- Never block main thread — use `Dispatchers.IO` or dedicated threads
- Min API 28 — verify compat before using newer APIs

## Key Reference Files

- **Requirements**: `.specs/specs.md`
- **Design/Architecture**: `.specs/design.md`
- **Tasks**: `.specs/task.md`
- **Decisions & resolved issues**: `.kiro/memory.md`
- **Kotlin-specific rules**: `AirPlayTV/app/src/main/java/com/airplay/tv/AGENTS.md`
- **Native-specific rules**: `AirPlayTV/app/src/main/cpp/AGENTS.md`

## Anti-Patterns

- Do NOT create documentation files without explicit request
- Do NOT over-engineer (this is a personal MVP)
- Do NOT explore `RPiPlay/`, `UxPlay/`, `vendor-docs/`, `.archive/` unless explicitly needed
- Do NOT assume modern hardware — TV is 7 years old
- Do NOT add DI frameworks, complex abstractions, or heavy dependencies
