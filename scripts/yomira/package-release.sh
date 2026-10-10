#!/usr/bin/env bash
#
# Prepares signed release APKs for publishing:
#   - renames them to Yomira-v<version>-<abi>.apk
#   - verifies the signature and checks the certificate against
#     docs/yomira/signing-certificate.sha256 (so a different key is never published)
#   - writes SHA256SUMS.txt and release-notes.md
#
# Usage: scripts/yomira/package-release.sh <apk-dir> <out-dir>
set -euo pipefail

SRC="${1:?usage: package-release.sh <apk-dir> <out-dir>}"
OUT="${2:?usage: package-release.sh <apk-dir> <out-dir>}"
ROOT="$(git rev-parse --show-toplevel)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
APKSIGNER="$BT/apksigner"
AAPT2="$BT/aapt2"
EXPECTED_CERT_FILE="$ROOT/docs/yomira/signing-certificate.sha256"

mkdir -p "$OUT"
rm -f "$OUT"/*.apk "$OUT/SHA256SUMS.txt"

version=""
shopt -s nullglob
apks=("$SRC"/*.apk)
if [ ${#apks[@]} -eq 0 ]; then
  echo "no APKs in $SRC" >&2
  exit 1
fi

for apk in "${apks[@]}"; do
  name="$(basename "$apk")"
  case "$name" in
    *unsigned*)
      echo "refusing unsigned APK: $name" >&2
      exit 1
      ;;
  esac

  v="$("$AAPT2" dump badging "$apk" | sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p")"
  pkg="$("$AAPT2" dump badging "$apk" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
  if [ "$pkg" != "com.lyraez.yomira" ]; then
    echo "unexpected package $pkg in $name" >&2
    exit 1
  fi
  version="$v"

  abi="universal"
  for a in armeabi-v7a arm64-v8a x86_64 x86; do
    case "$name" in *-"$a"-*) abi="$a"; break ;; esac
  done

  "$APKSIGNER" verify "$apk"
  cert="$("$APKSIGNER" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
  if [ -f "$EXPECTED_CERT_FILE" ]; then
    expected="$(tr -d '[:space:]' < "$EXPECTED_CERT_FILE" | tr 'A-F' 'a-f')"
    if [ "$cert" != "$expected" ]; then
      echo "signing certificate $cert does not match $EXPECTED_CERT_FILE ($expected)" >&2
      exit 1
    fi
  fi

  cp "$apk" "$OUT/Yomira-v$v-$abi.apk"
done

(cd "$OUT" && sha256sum Yomira-*.apk > SHA256SUMS.txt)

cat > "$OUT/release-notes.md" <<EOF
## Yomira $version

Escolha o APK pela arquitetura do aparelho (\`adb shell getprop ro.product.cpu.abilist\`):

| Arquivo | Para |
| --- | --- |
| \`Yomira-v$version-armeabi-v7a.apk\` | Fire TV Stick e TV Box de 32 bits |
| \`Yomira-v$version-arm64-v8a.apk\` | Aparelhos com sistema de 64 bits |
| \`Yomira-v$version-universal.apk\` | Qualquer aparelho ARM (arquivo maior) |

Todos os APKs usam o mesmo pacote (\`com.lyraez.yomira\`) e a mesma assinatura, então
instalam por cima da versão anterior sem perder dados.

Certificado de assinatura (SHA-256): \`$cert\`

### SHA-256
\`\`\`
$(cat "$OUT/SHA256SUMS.txt")
\`\`\`
EOF

echo "version=$version" >> "${GITHUB_OUTPUT:-/dev/null}"
cat "$OUT/SHA256SUMS.txt"
