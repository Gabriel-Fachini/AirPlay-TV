#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EMU_SCRIPT="$ROOT/scripts/emulator_tv.sh"
LOCAL_SCRIPT="$ROOT/scripts/local_audio_gate.sh"

run_local() {
  "$LOCAL_SCRIPT"
  echo "local ok"
}

run_emu() {
  "$EMU_SCRIPT" ensure >/dev/null
  serial="$("$EMU_SCRIPT" start)"
  "$EMU_SCRIPT" install "$serial" >/dev/null
  "$EMU_SCRIPT" launch "$serial" >/dev/null
  echo "emu ok serial=$serial"
  "$EMU_SCRIPT" log-dump "$serial"
}

case "${1:-all}" in
  local)
    run_local
    ;;
  emu)
    run_emu
    ;;
  all)
    run_local
    run_emu
    ;;
  *)
    echo "usage: $0 [local|emu|all]" >&2
    exit 1
    ;;
esac
