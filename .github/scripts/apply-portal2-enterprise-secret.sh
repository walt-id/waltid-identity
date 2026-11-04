#!/usr/bin/env bash
# Writes portal2-enterprise-secret from PORTAL2_ENTERPRISE_ENV without printing it.
set -euo pipefail

: "${PORTAL2_ENTERPRISE_ENV:?PORTAL2_ENTERPRISE_ENV is not set. Add the matching repository secret.}"
: "${KUBECONFIG:?KUBECONFIG is not set}"
namespace="${1:?namespace is required}"

env_file="$(mktemp)"
trap 'rm -f "$env_file"' EXIT
printf '%s\n' "$PORTAL2_ENTERPRISE_ENV" > "$env_file"

present="$(awk -F= '/^[A-Za-z_][A-Za-z0-9_]*=/ { print $1 }' "$env_file")"
missing=()
for name in \
  NUXT_ES_BASE_URL \
  NUXT_ES_EMAIL \
  NUXT_ES_PASSWORD \
  NUXT_ES_EUDI_REF_ISSUER_TARGET \
  NUXT_ES_FRANCE_IDENTITE_ISSUER_TARGET \
  NUXT_ES_DYOU_ISSUER_TARGET
do
  grep -qxF "$name" <<< "$present" || missing+=("$name")
done
if [[ ${#missing[@]} -gt 0 ]]; then
  echo "::error::PORTAL2_ENTERPRISE_ENV is missing: ${missing[*]}"
  exit 1
fi

kubectl create secret generic portal2-enterprise-secret \
  --namespace "$namespace" \
  --from-env-file="$env_file" \
  --dry-run=client -o yaml | kubectl apply -f -
