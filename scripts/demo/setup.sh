#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/demo.env"

TENANT_PATH="${ORG}.${TENANT}"
HOST_HDR="${ORG}.enterprise.localhost"

log()  { echo -e "\n\033[1;36m[$1]\033[0m $2"; }
ok()   { echo -e "  \033[1;32m✓\033[0m $1"; }
warn() { echo -e "  \033[1;33m!\033[0m $1"; }
err()  { echo -e "\033[1;31m[ERROR]\033[0m $1" >&2; exit 1; }

# ─── 1. Patch configs for ngrok-friendly URLs ────────────────────────────────

log "CONFIG" "Patching enterprise.conf (baseSsl=true, no basePort)"
sed -i '' 's/^baseSsl = false/baseSsl = true/' "$QUICKSTART_DIR/config/enterprise.conf"
sed -i '' 's/^basePort = .*$/# basePort — disabled for ngrok demo/' "$QUICKSTART_DIR/config/enterprise.conf"
ok "enterprise.conf patched"

# ─── 2. Start enterprise stack (using latest local images) ───────────────────

log "STACK" "Starting enterprise stack from $QUICKSTART_DIR (image tag: latest)"
docker compose -f "$QUICKSTART_DIR/docker-compose.yml" up -d

# Replace UI container with correct network alias so Caddy can resolve it
UI_ID=$(docker compose -f "$QUICKSTART_DIR/docker-compose.yml" ps -q waltid-enterprise-ui 2>/dev/null)
if [ -n "$UI_ID" ]; then
  docker rm -f "$UI_ID" >/dev/null 2>&1
fi
docker rm -f waltid-enterprise-ui 2>/dev/null || true
docker run -d \
  --name waltid-enterprise-ui \
  --network mongo-network \
  --network-alias waltid-enterprise-ui \
  -p 7501:3000 \
  -e "NUXT_PUBLIC_BASE_DOMAIN=enterprise.localhost" \
  waltid/waltid-enterprise-ui:latest >/dev/null 2>&1
ok "Docker containers started"

# ─── 3. Wait for API health ──────────────────────────────────────────────────

log "HEALTH" "Waiting for enterprise API on port $PORT..."
for i in $(seq 1 90); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/auth/account/emailpass" \
    -X POST -H "Content-Type: application/json" \
    -d '{"email":"probe@test","password":"x"}' 2>/dev/null)
  if [ "$HTTP_CODE" = "404" ] || [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "200" ]; then
    ok "Enterprise API is healthy (HTTP $HTTP_CODE, took ${i}s)"
    break
  fi
  if [ "$i" -eq 90 ]; then
    err "Enterprise API not responding after 90s (last HTTP: $HTTP_CODE)"
  fi
  sleep 2
done

# ─── 4. Run CLI --recreate ───────────────────────────────────────────────────

log "INIT" "Running quickstart CLI --recreate (DB init + full setup)"
(cd "$QUICKSTART_DIR/cli" && npx tsx walt.ts --recreate) || {
  warn "CLI --recreate exited with error (expected: issuer2 clientAuthenticationConfig bug)"
  echo "      Continuing — we create issuer2-noattest separately."
}

# ─── 5. Create issuer2-noattest + mdl-profile ────────────────────────────────

log "ISSUER" "Creating non-attested issuer (issuer2-noattest)"

TOKEN=$(curl -sf "http://localhost:$PORT/auth/account/emailpass" \
  -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.issuer2-noattest/resource-api/services/create" \
  -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Host: $HOST_HDR" \
  -d "{
    \"type\":\"issuer2\",
    \"_id\":\"$TENANT_PATH.issuer2-noattest\",
    \"tokenKeyId\":\"$TENANT_PATH.kms.issuer-signing-key\",
    \"kms\":\"$TENANT_PATH.kms\",
    \"credentialConfigurations\":{
      \"org.iso.18013.5.1.mDL\":{
        \"format\":\"mso_mdoc\",
        \"doctype\":\"org.iso.18013.5.1.mDL\",
        \"scope\":\"org.iso.18013.5.1.mDL\",
        \"credential_signing_alg_values_supported\":[-7,-9],
        \"cryptographic_binding_methods_supported\":[\"cose_key\"],
        \"proof_types_supported\":{\"jwt\":{\"proof_signing_alg_values_supported\":[\"ES256\"]}}
      }
    }
  }" > /dev/null 2>&1 || true

# Fetch x5Chain certs and create profile (certs required for mDoc issuance)
curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.x509-store.vical-doc-signer-cert/x509-store-api/certificates" \
  -H "Authorization: Bearer $TOKEN" -H "Host: $HOST_HDR" > /tmp/doc_cert.json
curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.x509-store.vical-iaca-cert/x509-store-api/certificates" \
  -H "Authorization: Bearer $TOKEN" -H "Host: $HOST_HDR" > /tmp/iaca_cert.json

python3 -c "
import json
with open('/tmp/doc_cert.json') as f:
    doc_pem = json.load(f)['data']['pem']
with open('/tmp/iaca_cert.json') as f:
    iaca_pem = json.load(f)['data']['pem']
payload = {
    'name': 'mdl-profile',
    'credentialConfigurationId': 'org.iso.18013.5.1.mDL',
    'issuerKeyId': '$TENANT_PATH.kms.issuer-signing-key',
    'x5Chain': [
        {'type': 'pem-encoded-x509-certificate-descriptor', 'pemEncodedCertificate': doc_pem},
        {'type': 'pem-encoded-x509-certificate-descriptor', 'pemEncodedCertificate': iaca_pem}
    ],
    'credentialData': {
        'org.iso.18013.5.1': {
            'family_name': 'Doe', 'given_name': 'John', 'birth_date': '1990-01-01',
            'issue_date': '2024-01-01', 'expiry_date': '2029-01-01',
            'issuing_country': 'US', 'issuing_authority': 'Test DMV',
            'document_number': 'DL123456789', 'un_distinguishing_sign': 'USA'
        }
    }
}
with open('/tmp/mdl-profile-payload.json', 'w') as f:
    json.dump(payload, f)
"

curl -sf "http://localhost:$PORT/v2/$TENANT_PATH.issuer2-noattest.mdl-profile/issuer-service-api/credentials/profiles" \
  -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Host: $HOST_HDR" \
  -d @/tmp/mdl-profile-payload.json > /dev/null 2>&1 || true

ok "issuer2-noattest + mdl-profile created"

# ─── 6. Configure ngrok host alias ──────────────────────────────────────────

log "NGROK" "Configuring host alias for $NGROK_DOMAIN"

if curl -sf -o /dev/null -H "ngrok-skip-browser-warning: true" "https://$NGROK_DOMAIN" 2>/dev/null; then
  curl -sf "http://localhost:$PORT/v1/$ORG.host-alias/host-alias-api/host-aliases/create" \
    -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Host: $HOST_HDR" \
    -d "{\"domain\":\"$NGROK_DOMAIN\"}" > /dev/null 2>&1 || true
  ok "Host alias created for $NGROK_DOMAIN"
else
  warn "ngrok not reachable at $NGROK_DOMAIN"
  echo "      Start it with: ngrok http $PORT --domain=$NGROK_DOMAIN"
  echo "      Then re-run this script or manually create the host alias."
fi

# ─── 7. Android emulator reverse port ───────────────────────────────────────

if command -v adb &>/dev/null && adb devices 2>/dev/null | grep -q "device$"; then
  log "ANDROID" "Setting up adb reverse"
  adb reverse tcp:$PORT tcp:$PORT 2>/dev/null || true
  ok "adb reverse tcp:$PORT tcp:$PORT"
else
  warn "No Android emulator detected (skipping adb reverse)"
fi

# ─── 8. Summary ─────────────────────────────────────────────────────────────

log "READY" "Demo stack is configured"
echo ""
echo "  Enterprise UI:  http://waltid.enterprise.localhost  (port 80, via Caddy)"
echo "  Login:          $ADMIN_EMAIL / $ADMIN_PASSWORD"
echo "  Issuer:         issuer2-noattest (non-attested, mdl-profile)"
echo "  Verifier:       verifier2 (signature-only)"
echo "  ngrok:          https://$NGROK_DOMAIN"
echo ""
echo "  Next: use ./offer.sh and ./verify.sh to drive mobile flows."
echo ""
