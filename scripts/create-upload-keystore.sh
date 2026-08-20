#!/usr/bin/env bash
# Generate a Play upload keystore and print the GitHub secret values.
# Keep the .jks file offline. Never commit it.
set -euo pipefail

OUT="${1:-upload-keystore.jks}"
ALIAS="${2:-espflasher}"

if [[ -e "$OUT" ]]; then
  echo "Refusing to overwrite $OUT" >&2
  exit 1
fi

echo "You will be prompted for a keystore password (use the same value for the key password)."
keytool -genkeypair \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=ESP Flasher, OU=Mobile, O=janisxyz, L=Basel, C=CH"

echo
echo "GitHub Actions secrets"
echo "  ESPFLASHER_KEY_ALIAS=$ALIAS"
echo "  ESPFLASHER_STORE_PASSWORD=<the password you just typed>"
echo "  ESPFLASHER_KEY_PASSWORD=<same password>"
echo "  ESPFLASHER_KEYSTORE_BASE64="
if base64 -w0 "$OUT" >/dev/null 2>&1; then
  base64 -w0 "$OUT"
  echo
else
  base64 "$OUT"
fi
echo
echo "Store $OUT somewhere safe (password manager / offline backup)."
echo "Google Play App Signing will hold the app signing key; this file is only the upload key."
