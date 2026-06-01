#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/demo.env"

TENANT_PATH="${ORG}.${TENANT}"
SESSION_ID_FILE="$SCRIPT_DIR/.last-session-id"

if [ ! -f "$SESSION_ID_FILE" ]; then
  echo "No session ID found. Run ./verify.sh first."
  exit 1
fi

SESSION_ID=$(cat "$SESSION_ID_FILE")

TOKEN=$(curl -sf "http://localhost:$PORT/auth/account/emailpass" \
  -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

RESULT=$(curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.verifier2.$SESSION_ID/verifier2-service-api/verification-session/info" \
  -H "Authorization: Bearer $TOKEN" -H "Host: ${ORG}.enterprise.localhost")

echo ""
echo "$RESULT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
session = d.get('session', d)
status = session.get('status', 'UNKNOWN')
colors = {'SUCCESSFUL': '\033[1;32m', 'FAILED': '\033[1;31m', 'PENDING': '\033[1;33m'}
color = colors.get(status, '\033[1;33m')
print(f'Session: $SESSION_ID')
print(f'Status:  {color}{status}\033[0m')
creds = session.get('presented_credentials', [])
if creds:
    print(f'  Presented: {len(creds)} credential(s)')
policy_results = session.get('policy_results', {})
if policy_results:
    for pid, result in (policy_results.items() if isinstance(policy_results, dict) else []):
        print(f'  Policy {pid}: {result}')
"
echo ""
