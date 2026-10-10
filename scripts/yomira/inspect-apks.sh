#!/usr/bin/env bash
#
# Checks the TV-facing parts of every APK in a directory and writes apk-report.txt.
#
# Fails when an APK would not show up or install on a Fire TV / Android TV device:
# missing LEANBACK_LAUNCHER, missing banner, required touchscreen or leanback feature,
# or an ABI split without native libraries for its ABI.
#
# Usage: scripts/yomira/inspect-apks.sh <apk-dir>
set -euo pipefail

DIR="${1:?usage: inspect-apks.sh <apk-dir>}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
AAPT2="$(ls -d "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)"
if [ -z "$AAPT2" ]; then
  echo "aapt2 not found under \$ANDROID_HOME/build-tools" >&2
  exit 1
fi

REPORT="$DIR/apk-report.txt"
: > "$REPORT"
status=0

for apk in "$DIR"/*.apk; do
  [ -e "$apk" ] || { echo "no APKs in $DIR" >&2; exit 1; }
  name="$(basename "$apk")"
  badging="$("$AAPT2" dump badging "$apk")"
  manifest="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$apk")"
  libs="$(unzip -l "$apk" | awk '{print $4}' | grep '^lib/' | cut -d/ -f2 | sort -u | tr '\n' ' ')"

  {
    echo "== $name"
    echo "$badging" | grep -E "^package:" | sed "s/ platformBuildVersion.*//"
    echo "$badging" | grep -E "^(sdkVersion|targetSdkVersion):"
    echo "native ABIs: ${libs:-none}"
    echo "$badging" | grep -E "^(leanback-launchable-activity|launchable-activity):" | sed "s/  label=.*//"
    echo "$badging" | grep -E "uses-feature(-not-required)?:.*(touchscreen|leanback)" || true
    echo "size: $(du -h "$apk" | cut -f1)"
    echo
  } >> "$REPORT"

  fail() { echo "FAIL $name: $1" | tee -a "$REPORT" >&2; status=1; }

  echo "$badging" | grep -q "^leanback-launchable-activity:" || fail "no LEANBACK_LAUNCHER activity"
  echo "$manifest" | grep -q "android:banner" || fail "application has no android:banner"
  if echo "$badging" | grep -E "^uses-feature: name='android.hardware.touchscreen'" >/dev/null; then
    fail "touchscreen is required"
  fi
  if echo "$badging" | grep -E "^uses-feature: name='android.software.leanback'" >/dev/null; then
    fail "leanback is required (phones could not install it)"
  fi
  for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    case "$name" in
      *-"$abi"-*)
        echo " $libs " | grep -q " $abi " || fail "split $abi carries no lib/$abi"
        ;;
    esac
  done
done

cat "$REPORT"
exit $status
