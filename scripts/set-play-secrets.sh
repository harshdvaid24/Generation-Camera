#!/usr/bin/env bash
# Pushes the signing + Play credentials into this repo's GitHub Actions secrets.
# Run it yourself from a normal terminal: bash scripts/set-play-secrets.sh
# Reads ~/keystores/generation-camera/keystore.env and play-service-account.json; nothing is stored in the repo.
set -euo pipefail

KEYS_DIR="${KEYS_DIR:-$HOME/keystores/generation-camera}"
SA_JSON="${SA_JSON:-$KEYS_DIR/play-service-account.json}"

cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source "$KEYS_DIR/keystore.env"
[ -f "$SA_JSON" ] || { echo "missing $SA_JSON" >&2; exit 1; }

base64 -i "$KEYSTORE_FILE" | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD --body "$KEYSTORE_PASSWORD"
gh secret set KEY_ALIAS --body "$KEY_ALIAS"
gh secret set KEY_PASSWORD --body "$KEY_PASSWORD"
gh secret set PLAY_SERVICE_ACCOUNT_JSON < "$SA_JSON"
gh variable set PLAY_RELEASE_STATUS --body "${PLAY_RELEASE_STATUS:-draft}"

echo "--- secrets now set:"
gh secret list
