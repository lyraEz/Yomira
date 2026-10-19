#!/usr/bin/env bash
#
# Summarises every APK in a directory (package, SDK levels, ABIs, launcher, size) and writes
# apk-report.txt.
#
# Fails when an APK has no launcher activity or an ABI split carries no native libraries for
# its ABI.
#
# Usage: scripts/yomira/inspect-apks.sh <apk-dir>
set -euo pipefail
# Checks read captured output through here-strings: with pipefail, `echo "$big" | grep -q`
# fails at random when grep exits before echo has written everything (SIGPIPE).

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
  libs="$(unzip -l "$apk" | awk '$4 ~ /^lib\// {split($4, p, "/"); print p[2]}' | sort -u | tr '\n' ' ')"

  {
    echo "== $name"
    grep -E "^package:" <<<"$badging" | sed "s/ platformBuildVersion.*//"
    grep -E "^(minSdkVersion|sdkVersion|targetSdkVersion):" <<<"$badging"
    echo "native ABIs: ${libs:-none}"
    grep -E "^launchable-activity:" <<<"$badging" | sed "s/  label=.*//"
    echo "size: $(du -h "$apk" | cut -f1)"
    echo
  } >> "$REPORT"

  fail() { echo "FAIL $name: $1" | tee -a "$REPORT" >&2; status=1; }

  grep -q "^launchable-activity:" <<<"$badging" || fail "no launcher activity"
  for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    case "$name" in
      *-"$abi"-*)
        grep -q " $abi " <<<" $libs " || fail "split $abi carries no lib/$abi"
        ;;
    esac
  done
done

cat "$REPORT"
exit $status
