#!/usr/bin/env bash
# Install the real signed Android candidate on the API29 emulator.
# Retry ONLY adb transport/install failures, never campaign/runtime assertions.
set -Eeuo pipefail

shard="${1:?Expected numeric first faction index}"
[[ "$shard" =~ ^(0|[1-9][0-9]*)$ ]] || {
  echo "Invalid campaign shard: $shard" >&2
  exit 2
}

install_checked() {
  local apk="${1:?APK path required}" attempt
  [[ -s "$apk" ]] || { echo "Missing actual APK: $apk" >&2; return 1; }
  for attempt in 1 2 3; do
    echo "Installing $apk (ADB attempt $attempt/3)"
    if timeout --signal=INT --kill-after=8s 45s adb wait-for-device \
        && timeout --signal=INT --kill-after=8s 100s adb install -r "$apk"; then
      echo "PASS: Android PackageManager accepted $apk"
      return 0
    fi
    echo "ADB transport/install attempt $attempt failed; recovering daemon" >&2
    timeout 12s adb kill-server || true
    timeout 12s adb start-server || true
    sleep 4
  done
  echo "FAIL: Android PackageManager did not accept $apk after bounded retries" >&2
  return 1
}

if [[ "$shard" == "0" ]]; then
  # Real data-preserving upgrade from first public signed release, not a
  # fresh-install substitute. Preserve the original signer and app data.
  install_checked package/reference/Catastrophic-Diplomacy-0.1.4-rc.1-Android.apk
fi
install_checked package/Catastrophic-Diplomacy-Android.apk
