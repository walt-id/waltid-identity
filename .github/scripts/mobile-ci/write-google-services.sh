#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
android_app="$identity_dir/waltid-applications/waltid-wallet-demo-compose/androidApp"

if [[ ! -d "$android_app" ]]; then
  echo "Compose androidApp module not present; skipping google-services.json."
  exit 0
fi

write_if_present() {
  local dest="$1"
  local content="${2:-}"
  local label="$3"

  if [[ -z "$content" ]]; then
    echo "Skipping $label google-services.json (secret not provided)."
    return 0
  fi

  GOOGLE_SERVICES_JSON_CONTENT="$content" python3 - <<'PY'
import json
import os

json.loads(os.environ["GOOGLE_SERVICES_JSON_CONTENT"])
PY

  mkdir -p "$(dirname "$dest")"
  umask 077
  printf '%s\n' "$content" > "$dest"
  echo "Wrote $label google-services.json."
}

write_if_present \
  "$android_app/src/preview/google-services.json" \
  "${GOOGLE_SERVICES_PREVIEW_JSON:-}" \
  "preview"

write_if_present \
  "$android_app/src/production/google-services.json" \
  "${GOOGLE_SERVICES_PRODUCTION_JSON:-}" \
  "production"
