#!/usr/bin/env bash
# Branch deploys rewrite issuer2.ingress.host in helm-values.json. The issuer URL must follow that host.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

cp -R "$ROOT/helm-charts" "$WORKDIR/helm-charts"
cp "$ROOT/.github/workflows/data/helm-values.json" "$WORKDIR/helm-values.json"
sed -i.bak 's/issuer2\.portal\.test\.waltid\.cloud/issuer2.example.waltid.cloud/g' "$WORKDIR/helm-values.json"

helm dependency build "$WORKDIR/helm-charts/waltid" >/dev/null
rendered="$(helm template waltid-helm-release "$WORKDIR/helm-charts/waltid" -f "$WORKDIR/helm-values.json")"

issuer_url="$(printf '%s\n' "$rendered" | awk '
  $0 ~ /name: ISSUER2_BASE_URL/ { found=1; next }
  found && $1 == "value:" { gsub(/"/, "", $2); print $2; exit }
')"

if [[ "$issuer_url" != "https://issuer2.example.waltid.cloud" ]]; then
  echo "FAIL: ISSUER2_BASE_URL is ${issuer_url:-<missing>}, expected https://issuer2.example.waltid.cloud"
  exit 1
fi
echo "PASS: ISSUER2_BASE_URL follows issuer2.ingress.host"
