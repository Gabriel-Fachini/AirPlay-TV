#!/bin/bash
# Quick project status for AI agents
# Usage: ./scripts/status.sh

set -e
cd "$(dirname "$0")/.."

echo "=== Build Status ==="
cd AirPlayTV && ./gradlew assembleDebug --quiet 2>&1 | tail -3
cd ..

echo ""
echo "=== Source Stats ==="
echo "Kotlin files: $(find AirPlayTV/app/src -name '*.kt' | wc -l | tr -d ' ')"
echo "C/C++ files:  $(find AirPlayTV/app/src -name '*.cpp' -o -name '*.h' -o -name '*.c' | wc -l | tr -d ' ')"
echo "Total lines:  $(find AirPlayTV/app/src -name '*.kt' -o -name '*.cpp' -o -name '*.h' -o -name '*.c' | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}')"

echo ""
echo "=== Test Status ==="
cd AirPlayTV && ./gradlew test --quiet 2>&1 | tail -5
cd ..

echo ""
echo "=== Git Status ==="
git status --short | head -10
MODIFIED=$(git status --short | wc -l | tr -d ' ')
echo "($MODIFIED files changed)"
