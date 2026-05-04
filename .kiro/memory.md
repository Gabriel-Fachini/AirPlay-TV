# Project Memory — AirPlay TV MVP

Knowledge base of decisions, resolved issues, and critical facts.
Updated: 2026-05-02

---

## Current Status

- **Phase**: Phases 1–5 complete, Phase 6 in progress (integration & polish)
- **What works**: mDNS discovery, pairing, FairPlay, RTSP handshake, video mirroring (Mac + iPhone), photo/slideshow display
- **What doesn't**: Audio during mirroring (not yet implemented in MirrorServer), occasional decoder artifacts
- **Last validated on hardware**: 2026-05-02

---

## Key Technical Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **UxPlay core** (not RPiPlay) via NDK | More maintained (2026), better A/V sync, same porting effort |
| 2 | **NsdManager** for mDNS (not jmdns) | Native API, fewer dependencies |
| 3 | **MediaCodec H.264 + SurfaceView** | Hardware-accelerated, zero-copy rendering |
| 4 | **MediaCodec AAC + AudioTrack** | Fine-grained control for A/V sync |
| 5 | **MVVM simple** (no Hilt/Koin) | MVP, low overhead |
| 6 | **Coroutines** for async | Clean Kotlin integration, easy cancellation |
| 7 | **No auto-reconnect** | Avoid complex retry loops in MVP |
| 8 | **No Apple cert validation** | Trusted home network only |
| 9 | **1080p with 720p fallback** | Hardware validated via AirScreen app |
| 10 | **Task 1.2 skipped** | AirScreen app proves hardware capability — no need for validation app |

---

## Resolved Issues (Knowledge Base)

### Protocol & Authentication

**Pairing is mandatory for modern Apple clients**
- Adjusting feature flags alone does NOT skip pair-setup
- Solution: `AirPlayPairingManager` with Ed25519 + X25519 (Bouncy Castle)
- Feature flag `0x08000000` (Authentication_8) must be OFF but clients still do pair-setup/pair-verify
- Ref: https://emanuelecozzi.net/docs/airplay2/authentication/

**FairPlay fp-setup works with 2-stage stub**
- First request: 16 bytes → respond with fixed FairPlay data
- Second request: 164 bytes → respond with fixed FairPlay data
- No real DRM enforcement needed for mirroring

**SETUP response must be binary plist**
- Modern clients send `Content-Type: application/x-apple-binary-plist`
- Legacy RTSP header responses cause immediate disconnect

**GET_PARAMETER and SET_PARAMETER must be handled**
- Client queries `volume` → respond `volume: 0.0`
- Missing handler causes disconnect

### Video Pipeline

**Mirroring headers are little-endian** (NOT network byte order)
- `0x24000000` read as big-endian = 603MB (wrong!)
- `0x00000024` read as little-endian = 36 bytes (correct)
- Ref: https://openairplay.github.io/airplay-spec/screen_mirroring/stream_packets.html

**Codec config (SPS/PPS) arrives as type 0x01 packet**
- NOT inline in type 0x00 video frames
- Must reconfigure MediaCodec when received
- Skip reconfiguration if resolution unchanged (avoids losing IDR keyframe)

**Video is always encrypted (Mac), try decrypt first**
- AES-CTR with key derived from `AirPlayStreamKey{connectionID}` + shared secret
- `streamConnectionID` must be formatted as unsigned (`Long.toUnsignedString`) for key derivation
- Decrypt first, fallback to unencrypted (iPhone may skip encryption)

**Key derivation must match RPiPlay exactly**
- `eaeskey` = full 64-byte SHA512(masterKey[16] + sharedSecret[32])
- `videoKey` = SHA512("AirPlayStreamKey{id}" + eaeskey[0:16])[0:16]
- `videoIV` = SHA512("AirPlayStreamIV{id}" + eaeskey[0:16])[0:16]
- Ref: RPiPlay `lib/mirror_buffer.c`

**Preserve keyframes when queue is full**
- Before first submit to codec, discard non-IDR until an IDR is found
- Prevents decoder from starting on a partial GOP

**Aspect ratio must be recalculated on resolution change**
- `Output format changed` from MediaCodec gives real crop dimensions
- Portrait content (e.g., iPhone) must letterbox, not stretch

### Audio Pipeline

**C++ RTP receiver already strips RTP headers**
- `onAudioData()` receives raw AAC payload, NOT RTP packets
- DO NOT re-parse with RTPParser in Kotlin (causes "Invalid RTP version" errors)
- Parse RFC 3640 AU headers to extract AAC Access Units

### Stability

**NTP socket must bind() before use**
- Without `bind(0.0.0.0:0)`, source port is unstable across `sendto()` calls
- Mac responds to wrong port → NTP sync fails → TEARDOWN after ~30s
- Solution: `bind()` + `getsockname()` to get ephemeral port (matches RPiPlay)

**Session timeout: 30 seconds** (was 5s, caused false disconnects)
- Reference implementations use 30-60s
- Throttle activity events to 1/second (video arrives at 30-60 fps)

**iPhone gallery doesn't use HTTP photo path**
- Stays in mirroring mode, sends new codec configs (type 0x01)
- Ignore duplicate codec config if resolution unchanged

**Binary HTTP bodies truncate on null bytes**
- `requestBuffer += buffer` stops at `\0` in binary plist bodies
- Fix: `requestBuffer.append(buffer, bytesRead)`

### MediaCodec Tips

- `KEY_LOW_LATENCY = 1` reduces internal buffering (may not work on old hardware, use try-catch)
- `KEY_PRIORITY = 0` sets realtime priority
- `KEY_OPERATING_RATE = 30` hints expected FPS
- Sony KD-55X755F uses MediaTek OMX decoder — sensitive to malformed H.264

### UI & Identity

- Idle screen highlights `serviceName` (mDNS name users search for), not `deviceName`
- Visual identity: blue backdrop with glow, glass cards, sans-serif typography
- Shared visual elements in `ScreenChrome.kt`

---

## Phase Completion Log

| Phase | Status | Key Metric |
|-------|--------|------------|
| 1 — Research | ✅ Complete | UxPlay chosen, hardware validated via AirScreen |
| 2 — Base structure | ✅ Complete | 30+ files, MVVM + Compose + NDK |
| 3 — mDNS | ✅ Complete | TV visible on Mac/iPhone/iPad AirPlay list |
| 4 — Protocol | ✅ Complete | RTSP handshake 100%, pairing working |
| 5 — Media pipeline | ✅ Complete | Video decoding working (252+ frames, 55fps) |
| 6 — Integration | 🔄 In progress | Photo/slideshow support, audio RTP parsing |
| 7 — Validation | ⏳ Pending | — |
| 8 — Documentation | ⏳ Pending | — |

---

## C++ Module Structure

Refactored from monolithic `airplay_server.cpp` (1315 lines) into modules:

```
cpp/
├── airplay_server.cpp/h      # Main server, delegates to modules (544 lines)
├── native-lib.cpp             # JNI bridge
├── protocol/
│   ├── rtsp_handler.cpp/h     # RTSP method handlers (350 lines)
│   ├── fairplay_handler.cpp/h # Pairing + FairPlay (200 lines)
│   └── protocol_constants.cpp/h # Arrays, feature flags (100 lines)
├── network/
│   ├── rtp_receiver.cpp/h     # UDP RTP reception (350 lines)
│   ├── mirror_server.cpp/h    # TCP mirroring server (200 lines)
│   ├── ntp_client.cpp/h       # NTP time sync
│   └── network_utils.cpp/h    # Byte conversion, socket utils
└── third_party/playfair/      # FairPlay crypto
```

---

## Scope Changes

- **Task 1.2 skipped**: AirScreen app validates hardware, no test app needed (saves ~2-3 days)
- **AIRPLAY_FEATURES reduced**: Removed Video, VideoFairPlay, VideoVolumeControl, VideoHTTPLiveStreams bits (unsupported)
- **Photo/slideshow support added**: PUT /photo, PUT /slideshows/1, cacheOnly/displayCached with 412 fallback
