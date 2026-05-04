#!/usr/bin/env bash
set -euo pipefail

mode="${1:-dump}"
adb_args=()

if [[ -n "${ADB_SERIAL:-}" ]]; then
  adb_args=(-s "$ADB_SERIAL")
fi

run_logcat() {
  adb "${adb_args[@]}" logcat "$@" -v raw -s AirPlayAudio:V AirPlayProtocol:V AirPlayRTP:V '*:S'
}

summarize() {
  awk '
    /SETUP audio/ && !setup { setup=$0 }
    /Audio key=/ && !key { key=$0 }
    /Audio sync/ && !sync { sync=$0 }
    /Dropping audio payloads/ && !unsupported { unsupported=$0 }
    /Audio fail reason=/ { fail++; last_fail=$0 }
    /Audio buf valid=/ && !buf { buf=$0 }
    /Queue AU/ { queue_au++; if (!first_queue_au) first_queue_au=$0 }
    /First PCM/ { first_pcm++; if (!first_pcm_line) first_pcm_line=$0 }
    /Clock lock/ { clock_lock++; if (!first_clock_lock) first_clock_lock=$0 }
    /Skip ELD no-data/ { no_data++ }
    /AAC short payload/ { short_payload++ }
    /AAC AU overflow/ { overflow++ }
    /AAC no AU hdr/ { no_au_hdr++ }
    /AAC too small/ { too_small++ }
    /Audio pre-sync/ { pre_sync++ }
    END {
      if (setup) print setup
      if (key) print key
      if (sync) print sync
      if (unsupported) print unsupported
      if (no_data) print "ELD no-data count=" no_data
      if (pre_sync) print "pre-sync count=" pre_sync
      if (fail) print "audio-fail count=" fail
      if (last_fail) print last_fail
      if (buf) print buf
      if (first_clock_lock) print first_clock_lock
      if (first_queue_au) print first_queue_au
      if (first_pcm_line) print first_pcm_line
      if (!setup && !key && !sync && !unsupported && !no_data && !fail && !buf && !first_queue_au && !first_pcm_line) {
        print "no audio logs"
      }
    }
  '
}

case "$mode" in
  clear)
    adb "${adb_args[@]}" logcat -c
    ;;
  dump)
    run_logcat -d | summarize
    ;;
  raw)
    run_logcat -d
    ;;
  live)
    run_logcat | summarize
    ;;
  *)
    echo "usage: $0 [dump|raw|live|clear]" >&2
    exit 1
    ;;
esac
