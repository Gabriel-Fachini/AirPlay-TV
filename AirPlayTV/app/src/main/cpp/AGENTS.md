# Native C++ Scope

Instructions in this file apply only under `AirPlayTV/app/src/main/cpp/`.

## Purpose

- Owns low-level AirPlay protocol handling, mirroring transport, RTP/NTP networking, and JNI-facing native server glue
- Treat this tree as performance-sensitive and compatibility-sensitive for an older Android TV target

## Directory Guide

- `airplay_server.*`: native entrypoint and high-level coordination
- `protocol/`: RTSP handlers, FairPlay, protocol constants
- `network/`: mirror TCP server, RTP receiver, NTP client, socket helpers
- `third_party/playfair/`: vendored crypto code; avoid changing unless the task explicitly requires it

## Rules

- Preserve socket/resource cleanup on every failure path
- Keep byte-order assumptions explicit; mirroring packet headers are not always network-endian
- Minimize allocations and copies in hot packet paths
- Maintain JNI-facing behavior and signatures unless Kotlin callers are updated together
- Prefer extending the current module split over rebuilding monolithic handlers

## Known Gotchas

- NTP client must `bind()` before traffic to keep the source port stable
- Mirror/video key derivation must stay byte-for-byte compatible with the reference behavior already validated in the project
- Binary plist and other raw HTTP bodies must be treated as binary-safe buffers, never C strings
