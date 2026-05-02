#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[verify_video_pipeline] Running focused unit tests"
./gradlew testDebugUnitTest --tests "com.airplay.tv.media.VideoDecoderTest" --tests "com.airplay.tv.protocol.AirPlayMirroringSessionTest"

echo "[verify_video_pipeline] Building debug APK"
./gradlew assembleDebug

echo "[verify_video_pipeline] OK"
