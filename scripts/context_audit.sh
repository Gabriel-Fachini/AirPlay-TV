#!/bin/bash
# Quick token/context footprint audit for agent sessions.

set -euo pipefail
cd "$(dirname "$0")/.."

echo "== Root entry files =="
find . -maxdepth 1 -type f | sed 's#^\./##' | sort

echo
echo "== Root doc line count =="
wc -l README.md AGENTS.md INDEX.md .specs/specs.md .specs/design.md .specs/task.md .kiro/memory.md 2>/dev/null

echo
echo "== Large source files (>300 lines) =="
find AirPlayTV/app/src/main -type f \( -name '*.kt' -o -name '*.cpp' -o -name '*.h' -o -name '*.c' \) \
  | xargs wc -l 2>/dev/null \
  | awk '$1 > 300 {print}' \
  | sort -nr

echo
echo "== Root historical docs still exposed =="
find . -maxdepth 1 -type f \( -name '*plan*.md' -o -name '*results*.md' -o -name '*refactor*.md' \) | sed 's#^\./##' | sort

echo
echo "== Session checklist =="
echo "1. Start with AGENTS.md"
echo "2. Read .specs/design.md"
echo "3. Open the scoped AGENTS for the target layer"
echo "4. Touch 2-4 code files before widening scope"
