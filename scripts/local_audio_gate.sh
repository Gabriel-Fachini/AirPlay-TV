#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$ROOT/AirPlayTV"

cd "$APP_DIR"

./gradlew \
  testDebugUnitTest \
  --tests 'com.airplay.tv.protocol.AudioFrameDecryptorSelectorTest' \
  --tests 'com.airplay.tv.protocol.AudioStreamConfigTest' \
  --tests 'com.airplay.tv.protocol.AirPlayMirroringSessionTest'

./gradlew assembleDebug
