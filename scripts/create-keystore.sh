#!/usr/bin/env bash
# Creates YOUR private release signing key. Run once, then back it up. Never commit it.
set -euo pipefail
OUT="${1:-socmed-release.jks}"
ALIAS="${2:-socmed}"
if [ -e "$OUT" ]; then echo "$OUT already exists — refusing to overwrite."; exit 1; fi
keytool -genkeypair -v -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500
echo
echo "Created $OUT. BACK IT UP (password manager + a second place)."
echo "If you lose it you can never update the installed app — you'd have to uninstall (losing local data)."
echo
echo "For local builds create keystore.properties in the project root:"
echo "  storeFile=$OUT"
echo "  storePassword=…"
echo "  keyAlias=$ALIAS"
echo "  keyPassword=…"
echo
echo "For GitHub Actions add these repository secrets:"
echo "  KEYSTORE_BASE64   = $(printf 'output of: base64 -w0 %s' "$OUT")"
echo "  KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD"
