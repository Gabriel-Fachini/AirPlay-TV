# Kotlin App Scope

Instructions in this file apply only under `AirPlayTV/app/src/main/java/com/airplay/tv/`.

## Purpose

- Owns Android app flow, UI state, service orchestration, protocol/session bridging, and media decode/render integration
- Keep layering simple: `ui/` -> `service/` -> `protocol/` + `network/` + `media/`

## Directory Guide

- `ui/` and `ui/components/`: screens and state-driven rendering
- `service/`: session lifecycle and app/service coordination
- `protocol/`: Kotlin-side AirPlay session logic and pairing integration
- `network/`: Android network discovery helpers
- `media/`: MediaCodec decoders and A/V sync helpers
- `util/`: shared constants, logging, small utilities

## Rules

- Preserve the state flow `Idle -> Connecting -> Mirroring -> Idle` unless the task explicitly changes product behavior
- Never block the main thread; use coroutines or dedicated workers
- Release `MediaCodec`, `AudioTrack`, sockets, and listeners in error and shutdown paths
- Keep API 28 compatibility explicit before using newer Android APIs
- Prefer small edits in the existing module over new abstractions

## Known Gotchas

- `RTPParser.kt` must not be reintroduced into the audio path when C++ already stripped RTP headers
- `serviceName` is the user-facing AirPlay name on idle screens; do not replace it with `deviceName`
- Decoder reconfiguration is sensitive; avoid resetting codec state unless the stream metadata actually changed
