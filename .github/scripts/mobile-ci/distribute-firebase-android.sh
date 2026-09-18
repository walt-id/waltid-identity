#!/usr/bin/env bash
set -euo pipefail

apk="${APK_PATH:?APK_PATH is required}"
app_id="${FIREBASE_APP_ID:?FIREBASE_APP_ID is required}"
groups="${FIREBASE_GROUPS:?FIREBASE_GROUPS is required}"
project="${FIREBASE_PROJECT:-waltid-compose-app}"
notes_file="${RELEASE_NOTES_FILE:-}"

if [[ ! -f "$apk" ]]; then
  echo "::error::APK not found"
  exit 1
fi

if [[ -z "${FIREBASE_SERVICE_ACCOUNT:-}${FIREBASE_TOKEN:-}" ]]; then
  echo "::error::Set FIREBASE_SERVICE_ACCOUNT or FIREBASE_TOKEN to upload to App Distribution."
  exit 1
fi

workdir="${RUNNER_TEMP:-$(mktemp -d)}"
cleanup() {
  rm -f "${workdir}/firebase-sa.json"
}
trap cleanup EXIT

if [[ -n "${FIREBASE_SERVICE_ACCOUNT:-}" ]]; then
  sa="${workdir}/firebase-sa.json"
  umask 077
  printf '%s\n' "$FIREBASE_SERVICE_ACCOUNT" > "$sa"
  export GOOGLE_APPLICATION_CREDENTIALS="$sa"
fi

args=(
  appdistribution:distribute
  "$apk"
  --app "$app_id"
  --groups "$groups"
  --project "$project"
  --non-interactive
)

if [[ -n "$notes_file" && -f "$notes_file" ]]; then
  args+=(--release-notes-file "$notes_file")
fi

npx --yes firebase-tools@14 "${args[@]}"
