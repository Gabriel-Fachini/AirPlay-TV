#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$ROOT/AirPlayTV"
APP_ID="com.airplay.tv"
MAIN_ACTIVITY="com.airplay.tv/.MainActivity"
REQUESTED_AVD_NAME="${AVD_NAME:-airplay-tv-api28}"
SYSTEM_IMAGE="${SYSTEM_IMAGE:-system-images;android-28;android-tv;x86}"
PKG_PATH="${PKG_PATH:-$HOME/Library/Android/sdk}"

ANDROID_HOME="${ANDROID_HOME:-$PKG_PATH}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
EMU="$ANDROID_SDK_ROOT/emulator/emulator"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
APK="$APP_DIR/app/build/outputs/apk/debug/app-debug.apk"
RESOLVED_AVD_NAME=""

need_bin() {
  command -v "$1" >/dev/null 2>&1
}

need_path() {
  [[ -x "$1" ]]
}

require_sdk() {
  need_path "$ADB" || { echo "missing adb: $ADB" >&2; exit 1; }
  need_path "$EMU" || { echo "missing emulator: $EMU" >&2; exit 1; }
}

resolve_avd_name() {
  if [[ -n "$RESOLVED_AVD_NAME" ]]; then
    echo "$RESOLVED_AVD_NAME"
    return
  fi

  local avd_names=()
  while IFS= read -r avd_name; do
    [[ -n "$avd_name" ]] && avd_names+=("$avd_name")
  done < <("$EMU" -list-avds 2>/dev/null)
  if printf '%s\n' "${avd_names[@]}" | rg -x "$REQUESTED_AVD_NAME" >/dev/null 2>&1; then
    RESOLVED_AVD_NAME="$REQUESTED_AVD_NAME"
  elif [[ -z "${AVD_NAME:-}" && "${#avd_names[@]}" -eq 1 ]]; then
    RESOLVED_AVD_NAME="${avd_names[0]}"
  else
    RESOLVED_AVD_NAME="$REQUESTED_AVD_NAME"
  fi

  echo "$RESOLVED_AVD_NAME"
}

serial_for_avd() {
  local avd_name
  avd_name="$(resolve_avd_name)"
  "$ADB" devices | awk -v avd="$avd_name" '
    NR > 1 && $2 == "device" { print $1 }
  ' | while read -r serial; do
    name="$("$ADB" -s "$serial" emu avd name 2>/dev/null | tr -d "\r" | sed '/^OK$/d' | head -n1)"
    [[ "$name" == "$avd_name" ]] && echo "$serial"
  done | head -n1
}

wait_boot() {
  local serial="$1"
  "$ADB" -s "$serial" wait-for-device >/dev/null
  until [[ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
  done
}

ensure_avd() {
  require_sdk
  local avd_name
  avd_name="$(resolve_avd_name)"
  if "$EMU" -list-avds | rg -x "$avd_name" >/dev/null 2>&1; then
    echo "avd ok name=$avd_name"
    return
  fi

  need_path "$SDKMANAGER" || { echo "missing sdkmanager: $SDKMANAGER" >&2; exit 1; }
  need_path "$AVDMANAGER" || { echo "missing avdmanager: $AVDMANAGER" >&2; exit 1; }

  yes | "$SDKMANAGER" --install "platform-tools" "emulator" "$SYSTEM_IMAGE" >/dev/null
  echo "no" | "$AVDMANAGER" create avd -n "$avd_name" -k "$SYSTEM_IMAGE" --device "tv_1080p"
  echo "avd created name=$avd_name"
}

start_avd() {
  require_sdk
  local serial
  serial="$(serial_for_avd || true)"
  if [[ -n "$serial" ]]; then
    echo "$serial"
    return
  fi

  local avd_name
  avd_name="$(resolve_avd_name)"
  nohup "$EMU" -avd "$avd_name" -netdelay none -netspeed full >/tmp/"$avd_name".log 2>&1 &
  sleep 5
  serial=""
  until [[ -n "$serial" ]]; do
    serial="$(serial_for_avd || true)"
    sleep 2
  done
  wait_boot "$serial"
  echo "$serial"
}

install_app() {
  local serial="$1"
  [[ -f "$APK" ]] || (cd "$APP_DIR" && ./gradlew assembleDebug >/dev/null)
  "$ADB" -s "$serial" install -r "$APK" >/dev/null
  echo "install ok serial=$serial"
}

launch_app() {
  local serial="$1"
  "$ADB" -s "$serial" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
  "$ADB" -s "$serial" shell am start -n "$MAIN_ACTIVITY" >/dev/null
  echo "launch ok serial=$serial"
}

status_avd() {
  require_sdk
  local serial
  serial="$(serial_for_avd || true)"
  if [[ -z "$serial" ]]; then
    echo "emu down"
    return
  fi
  echo "emu up serial=$serial boot=$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
}

stop_avd() {
  require_sdk
  local serial
  serial="$(serial_for_avd || true)"
  [[ -n "$serial" ]] || { echo "emu down"; return; }
  "$ADB" -s "$serial" emu kill >/dev/null
  echo "emu stop serial=$serial"
}

log_dump() {
  local serial="$1"
  ADB_SERIAL="$serial" "$ROOT/scripts/audio_logs.sh" dump
}

log_raw() {
  local serial="$1"
  ADB_SERIAL="$serial" "$ROOT/scripts/audio_logs.sh" raw
}

case "${1:-status}" in
  ensure)
    ensure_avd
    ;;
  start)
    start_avd
    ;;
  install)
    install_app "${2:-$(start_avd)}"
    ;;
  launch)
    launch_app "${2:-$(start_avd)}"
    ;;
  status)
    status_avd
    ;;
  stop)
    stop_avd
    ;;
  log-dump)
    log_dump "${2:-$(start_avd)}"
    ;;
  log-raw)
    log_raw "${2:-$(start_avd)}"
    ;;
  *)
    echo "usage: $0 [ensure|start|install|launch|status|stop|log-dump|log-raw] [serial]" >&2
    exit 1
    ;;
esac
