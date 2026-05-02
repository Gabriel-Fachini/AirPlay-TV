---
inclusion: auto
---

# Project Structure

## Repository Organization

```
airplay-tv-mvp/
├── .git/                      # Git repository
├── .gitignore                 # Git ignore rules
├── .idea/                     # Android Studio settings
│
├── .kiro/                     # AI agent configuration
│   ├── agents.md              # Agent guidelines and conventions
│   ├── memory.md              # Project decisions and history
│   ├── ISSUE_TEMPLATE.md      # Issue reporting template
│   └── steering/              # Auto-included context files
│       ├── airplay-project-context.md
│       ├── product.md
│       ├── tech.md
│       └── structure.md
│
├── .specs/                    # Specification documents
│   ├── specs.md               # Requirements (functional & non-functional)
│   ├── design.md              # Technical design & architecture
│   └── task.md                # Implementation tasks (8 phases, 23 tasks)
│
├── app/                       # Android application (to be created)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/airplay/tv/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── AirPlayViewModel.kt
│   │   │   │   │   ├── UIStateManager.kt
│   │   │   │   │   └── components/
│   │   │   │   │       ├── IdleScreen.kt
│   │   │   │   │       ├── ConnectingScreen.kt
│   │   │   │   │       ├── MirroringScreen.kt
│   │   │   │   │       └── ErrorScreen.kt
│   │   │   │   ├── service/
│   │   │   │   │   ├── AirPlayService.kt
│   │   │   │   │   ├── SessionManager.kt
│   │   │   │   │   └── TelemetryCollector.kt
│   │   │   │   ├── network/
│   │   │   │   │   ├── mDNSModule.kt
│   │   │   │   │   └── NetworkUtils.kt
│   │   │   │   ├── protocol/
│   │   │   │   │   ├── ProtocolHandler.kt
│   │   │   │   │   ├── RTPParser.kt
│   │   │   │   │   └── native-lib.cpp
│   │   │   │   ├── media/
│   │   │   │   │   ├── VideoDecoder.kt
│   │   │   │   │   ├── AudioDecoder.kt
│   │   │   │   │   └── SyncManager.kt
│   │   │   │   └── util/
│   │   │   │       ├── Logger.kt
│   │   │   │       └── Constants.kt
│   │   │   ├── cpp/
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   ├── airplay-lib/
│   │   │   │   └── jni-bridge.cpp
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   └── activity_main.xml
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── colors.xml
│   │   │   │   └── drawable/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   └── build.gradle.kts
│
├── gradle/                    # Gradle wrapper
├── build.gradle.kts           # Root build configuration
├── settings.gradle.kts        # Project settings
│
├── README.md                  # Project overview
├── SETUP.md                   # Development environment setup
├── PROJECT_OVERVIEW.md        # Detailed project structure
├── EXECUTIVE_SUMMARY.md       # 5-minute executive summary
├── QUICK_REFERENCE.md         # Commands and snippets
└── DOCUMENTATION_INDEX.md     # Documentation guide
```

## Component Organization

### UI Layer (`ui/`)
- **Purpose**: User interface and presentation logic
- **Components**:
  - `AirPlayViewModel`: Business logic coordinator
  - `UIStateManager`: State machine for UI transitions
  - `components/`: Screen components (Idle, Connecting, Mirroring, Error)
- **Responsibilities**: State management, user interaction, UI rendering

### Service Layer (`service/`)
- **Purpose**: Core business logic and orchestration
- **Components**:
  - `AirPlayService`: Main service orchestrator
  - `SessionManager`: Session lifecycle management
  - `TelemetryCollector`: Performance metrics collection
- **Responsibilities**: Coordinate network, protocol, and media components

### Network Layer (`network/`)
- **Purpose**: Network discovery and communication
- **Components**:
  - `mDNSModule`: Service announcement via NsdManager
  - `NetworkUtils`: Network helper functions
- **Responsibilities**: mDNS service registration, network state monitoring

### Protocol Layer (`protocol/`)
- **Purpose**: AirPlay protocol implementation
- **Components**:
  - `ProtocolHandler`: JNI wrapper for native AirPlay library
  - `RTPParser`: RTP packet parsing and payload extraction
  - `native-lib.cpp`: JNI bridge to C/C++ code
- **Responsibilities**: RTSP handshake, RTP packet handling, protocol state

### Media Layer (`media/`)
- **Purpose**: Audio/video decoding and playback
- **Components**:
  - `VideoDecoder`: H.264 decoding via MediaCodec
  - `AudioDecoder`: AAC decoding via MediaCodec
  - `SyncManager`: Audio/video synchronization
- **Responsibilities**: Media decoding, rendering, A/V sync

### Utility Layer (`util/`)
- **Purpose**: Shared utilities and constants
- **Components**:
  - `Logger`: Structured logging with standardized tags
  - `Constants`: App-wide constants and configuration
- **Responsibilities**: Logging, configuration, helper functions

## Documentation Structure

### Specifications (`.specs/`)
- **specs.md**: What to build (requirements)
- **design.md**: How to build it (architecture)
- **task.md**: When to build it (implementation order)

### Agent Configuration (`.kiro/`)
- **agents.md**: Guidelines for AI agents
- **memory.md**: Project history and decisions
- **steering/**: Auto-included context files

### User Documentation (root)
- **README.md**: Quick start and overview
- **SETUP.md**: Environment setup guide
- **PROJECT_OVERVIEW.md**: Comprehensive project guide
- **EXECUTIVE_SUMMARY.md**: High-level summary

## Naming Conventions

### Files
- **Kotlin classes**: PascalCase (e.g., `AirPlayService.kt`)
- **Packages**: lowercase (e.g., `com.airplay.tv.service`)
- **Resources**: snake_case (e.g., `activity_main.xml`)
- **Native code**: snake_case (e.g., `jni_bridge.cpp`)

### Code
- **Classes**: PascalCase (`AirPlayService`, `VideoDecoder`)
- **Functions**: camelCase (`startSession()`, `parseRtpPacket()`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_BUFFER_SIZE`, `TAG_VIDEO`)
- **Variables**: camelCase (`currentSession`, `videoCodec`)
- **Private members**: camelCase with underscore prefix (`_state`)

## Module Dependencies

```
MainActivity
    ↓
AirPlayViewModel
    ↓
AirPlayService
    ↓
┌───────────┬──────────────┬──────────────┐
│           │              │              │
mDNSModule  ProtocolHandler SessionManager
            ↓              
    ┌───────┴───────┐
    │               │
VideoDecoder    AudioDecoder
    │               │
    └───────┬───────┘
            ↓
        SyncManager
```

## State Flow

```
Idle → Connecting → Mirroring → Idle
  ↑                     ↓
  └─────── Error ←──────┘
```

## Data Flow

```
Apple Device
    ↓ (RTP packets)
ProtocolHandler
    ↓ (H.264/AAC payloads)
┌──────────┬──────────┐
│          │          │
VideoDecoder  AudioDecoder
│          │          │
SurfaceView  AudioTrack
    ↓          ↓
   TV Screen + Speakers
```

## Configuration Files

- **AndroidManifest.xml**: App permissions, components, TV launcher
- **build.gradle.kts**: Dependencies, NDK config, build variants
- **CMakeLists.txt**: Native code compilation
- **local.properties**: Local SDK/NDK paths (not in git)
- **.gitignore**: Ignored files (build/, .idea/, local.properties)

## Resource Organization

```
res/
├── layout/
│   └── activity_main.xml      # Main activity layout
├── values/
│   ├── strings.xml            # Localized strings
│   ├── colors.xml             # Color palette
│   └── dimens.xml             # Dimensions (TV-optimized)
├── drawable/
│   └── ic_launcher.xml        # App icon
└── mipmap-*/                  # Launcher icons (various densities)
```

## Build Outputs

```
app/build/
├── outputs/
│   └── apk/
│       ├── debug/
│       │   └── app-debug.apk
│       └── release/
│           └── app-release.apk
├── intermediates/             # Intermediate build files
└── tmp/                       # Temporary files
```

## Key Principles

1. **Separation of concerns**: Each layer has a single responsibility
2. **Dependency direction**: UI → Service → Network/Protocol/Media
3. **No circular dependencies**: Enforce unidirectional data flow
4. **Minimal coupling**: Components communicate via interfaces
5. **Testability**: Each component can be tested in isolation
