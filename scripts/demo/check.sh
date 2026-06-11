#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/demo.env"

pass() { echo -e "  \033[1;32m✓\033[0m $1"; }
fail() { echo -e "  \033[1;31m✗\033[0m $1"; FAILURES=$((FAILURES + 1)); }
hint() { echo -e "    \033[2m→ $1\033[0m"; }

FAILURES=0

echo ""
echo "Pre-recording checklist"
echo "─────────────────────────────────────────"

# 1. Docker containers
if docker compose -f "$QUICKSTART_DIR/docker-compose.yml" ps --status running 2>/dev/null | grep -q "waltid-enterprise-api"; then
  pass "Docker: enterprise API running"
else
  fail "Docker: enterprise API not running"
  hint "Run ./setup.sh"
fi

if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "waltid-enterprise-ui"; then
  pass "Docker: enterprise UI running"
else
  fail "Docker: enterprise UI not running"
  hint "Run ./setup.sh"
fi

# 2. Enterprise API
if curl -sf "http://localhost:$PORT/auth/account/emailpass" \
  -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" > /dev/null 2>&1; then
  pass "Enterprise API: responding on port $PORT"
else
  fail "Enterprise API: not responding on port $PORT"
  hint "Check docker logs or wait for startup"
fi

# 3. ngrok
if curl -sf -o /dev/null -H "ngrok-skip-browser-warning: true" "https://$NGROK_DOMAIN" 2>/dev/null; then
  pass "ngrok: reachable at $NGROK_DOMAIN"
else
  fail "ngrok: not reachable at $NGROK_DOMAIN"
  hint "ngrok http $PORT --domain=$NGROK_DOMAIN"
fi

# 4. Android emulator
if command -v adb &>/dev/null && adb devices 2>/dev/null | grep -q "device$"; then
  pass "Android: emulator/device connected"
else
  fail "Android: no emulator/device connected"
  hint "Start Android emulator from Android Studio"
fi

# 5. adb reverse
if command -v adb &>/dev/null && adb reverse --list 2>/dev/null | grep -q "tcp:$PORT"; then
  pass "Android: adb reverse tcp:$PORT set"
else
  fail "Android: adb reverse tcp:$PORT not set"
  hint "adb reverse tcp:$PORT tcp:$PORT"
fi

# 6. iOS simulator
if xcrun simctl list devices booted 2>/dev/null | grep -q "Booted"; then
  pass "iOS: simulator booted"
else
  fail "iOS: no simulator booted"
  hint "Open Xcode → Window → Devices and Simulators, or: xcrun simctl boot <device-id>"
fi

echo ""
if [ "$FAILURES" -eq 0 ]; then
  echo -e "\033[1;32mAll checks passed. Ready to record.\033[0m"
else
  echo -e "\033[1;31m$FAILURES check(s) failed. Fix the issues above before recording.\033[0m"
fi
echo ""

exit "$FAILURES"
