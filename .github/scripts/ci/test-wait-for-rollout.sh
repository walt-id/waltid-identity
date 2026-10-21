#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WAIT="$SCRIPT_DIR/wait-for-rollout.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cat > "$tmp/kubectl" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$KUBECTL_LOG"
exit "$KUBECTL_EXIT"
EOF
chmod +x "$tmp/kubectl"

export PATH="$tmp:$PATH"
export KUBECTL_LOG="$tmp/log"

KUBECTL_EXIT=0 "$WAIT" test-identity-main issuer2 180s
grep -qx 'rollout status deployment/issuer2 -n test-identity-main --timeout=180s' "$KUBECTL_LOG"
echo "PASS: ready deployment"

if KUBECTL_EXIT=1 "$WAIT" identity-main issuer2 180s; then
  echo "FAIL: crashed deployment was treated as ready"
  exit 1
fi
echo "PASS: startup failure fails the rollout check"
