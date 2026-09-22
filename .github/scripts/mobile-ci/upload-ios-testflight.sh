#!/usr/bin/env bash
set -euo pipefail

ipa="${IPA_PATH:?IPA_PATH is required}"
notes_file="${RELEASE_NOTES_FILE:-}"
key_id="${APP_STORE_CONNECT_KEY_ID:?APP_STORE_CONNECT_KEY_ID is required}"
issuer_id="${APP_STORE_CONNECT_ISSUER_ID:?APP_STORE_CONNECT_ISSUER_ID is required}"
private_key="${APP_STORE_CONNECT_PRIVATE_KEY:?APP_STORE_CONNECT_PRIVATE_KEY is required}"

if [[ ! -f "$ipa" ]]; then
  echo "::error::IPA not found"
  exit 1
fi

workdir="${RUNNER_TEMP:-$(mktemp -d)}"
api_key_json="${workdir}/appstore-connect-api-key.json"
umask 077
python3 - "$api_key_json" <<'PY'
import json
import os
import pathlib
import sys

pathlib.Path(sys.argv[1]).write_text(
    json.dumps(
        {
            "key_id": os.environ["APP_STORE_CONNECT_KEY_ID"],
            "issuer_id": os.environ["APP_STORE_CONNECT_ISSUER_ID"],
            "key": os.environ["APP_STORE_CONNECT_PRIVATE_KEY"],
        }
    ),
    encoding="utf-8",
)
PY

if ! command -v fastlane >/dev/null 2>&1; then
  # See install-and-run-kdoctor: the runner image's untrusted aws/tap makes every
  # `brew install` emit a tap-trust warning. Drop it first.
  brew untap aws/tap 2>/dev/null || true
  brew install fastlane
fi

changelog=""
if [[ -n "$notes_file" && -f "$notes_file" ]]; then
  changelog="$(cat "$notes_file")"
fi

fastlane run upload_to_testflight \
  api_key_path:"$api_key_json" \
  ipa:"$ipa" \
  changelog:"$changelog" \
  skip_waiting_for_build_processing:true \
  skip_submission:true
