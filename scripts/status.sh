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
echo "Large files >300 lines: $(find AirPlayTV/app/src/main -type f \\( -name '*.kt' -o -name '*.cpp' -o -name '*.h' -o -name '*.c' \\) | xargs wc -l 2>/dev/null | awk '$1 > 300 {count++} END {print count+0}')"

echo ""
echo "=== Context Stats ==="
echo "Root docs lines: $(wc -l README.md AGENTS.md INDEX.md .specs/specs.md .specs/design.md .specs/task.md .kiro/memory.md 2>/dev/null | tail -1 | awk '{print $1}')"
echo "Root historical docs: $(find . -maxdepth 1 -type f \\( -name '*plan*.md' -o -name '*results*.md' -o -name '*refactor*.md' \\) | wc -l | tr -d ' ')"

echo ""
echo "=== Test Status ==="
cd AirPlayTV && ./gradlew test --quiet 2>&1 | tail -5
cd ..

echo ""
echo "=== Git Status ==="
git status --short | head -10
MODIFIED=$(git status --short | wc -l | tr -d ' ')
echo "($MODIFIED files changed)"
