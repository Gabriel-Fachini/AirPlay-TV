---
inclusion: auto
---

# Product Summary

## What We're Building

AirPlay TV MVP is a minimalist AirPlay mirroring receiver for Android TV that enables screen mirroring from Apple devices (Mac, iPhone, iPad) to a Sony Android TV.

## Target Use Case

- **Primary user**: Project author (personal home use)
- **Environment**: Home Wi-Fi network
- **Devices**: Mac (macOS Tahoe), iPhone/iPad (iOS 26)
- **Hardware**: Sony KD-55X755F (55", Android TV 9, ~7 years old)

## Core Features (MVP)

- Screen mirroring with audio from Apple devices
- Support for Mac, iPhone, and iPad
- Single active session at a time
- Immediate connection (no PIN required)
- Target resolution: 1080p (fallback to 720p)
- Maximum latency: 1000ms
- Local installation via APK

## Out of Scope (MVP)

- Direct media casting (mirroring only)
- Full AirPlay 2 support
- Multiple simultaneous sessions
- Background operation
- Play Store distribution
- PIN authentication
- Automatic reconnection

## Success Criteria

The MVP is successful when:

1. TV appears in AirPlay list on Mac/iPhone/iPad
2. Connection established without PIN in < 5 seconds
3. Video rendered fullscreen (720p or 1080p)
4. Audio synchronized with video (< 100ms desync)
5. Perceived latency < 1000ms
6. Stable for 30+ minutes without crashes
7. Session can be manually terminated
8. App returns to idle state after disconnection

## Key Principles

- **Functionality over performance**: Make it work first
- **Performance over elegance**: Optimize second
- **Simplicity over abstraction**: Avoid over-engineering
- **Test on real hardware**: Validate early and often
- **Document decisions**: Keep memory.md updated
