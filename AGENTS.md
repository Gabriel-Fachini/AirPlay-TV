# AirPlay TV MVP — Agent Rules

## Project

- AirPlay mirroring receiver for Android TV (Sony KD-55X755F, API 28, ~7 years old)
- Personal/home use, single-session, no PIN authentication
- Stack: Kotlin + C++/NDK, MVVM simple, Coroutines
- Kotlin source: `AirPlayTV/app/src/main/java/com/airplay/tv/`
- Native source: `AirPlayTV/app/src/main/cpp/`

## Session Entry

- Default reading flow: `AGENTS.md -> .specs/design.md -> scoped AGENTS -> target files`
- Normal tasks should name the layer early: `ui`, `service`, `protocol`, `media`, or `cpp`
- Prefer narrowing work to 2-4 files instead of broad repo sweeps

## Build & Run

```bash
cd AirPlayTV && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep "AirPlay"
cd AirPlayTV && ./gradlew test
```

## Architecture

- Layers: `ui/` -> `service/` -> `protocol/` + `network/` + `media/`
- UI states: `Idle -> Connecting -> Mirroring -> Idle` or `Error -> Idle`
- Video: Mirror TCP -> `AirPlayMirroringSession` -> `VideoDecoder` -> `SurfaceView`
- Audio: RTP UDP -> `ProtocolHandler` -> `AudioDecoder` -> `AudioTrack`

## Immutable Decisions

- `NsdManager` for mDNS, not jmdns
- `MediaCodec` H.264 + `SurfaceView`, not ExoPlayer
- `MediaCodec` AAC + `AudioTrack`
- MVVM simple, no DI framework
- Coroutines for async
- Single session, no auto-reconnect
- Min API 28, target Android TV

## Key References

- Requirements: `.specs/specs.md`
- Design: `.specs/design.md`
- Tasks: `.specs/task.md`
- Facts and gotchas: `.kiro/memory.md`
- Kotlin rules: `AirPlayTV/app/src/main/java/com/airplay/tv/AGENTS.md`
- Native rules: `AirPlayTV/app/src/main/cpp/AGENTS.md`

## Anti-Patterns

- Do not create docs without explicit request
- Do not over-engineer this MVP
- Do not start in `UxPlay/`, `RPiPlay/`, `vendor-docs/`, or `.archive/`
- Do not assume modern hardware characteristics
- Do not add new heavy abstractions or dependencies unless the task requires it
