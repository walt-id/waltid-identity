#!/usr/bin/env bash

require_e2e_command() {
  command -v "$1" >/dev/null 2>&1 || err "Missing required command: $1"
}

e2e_curl() {
  local label="$1"
  shift
  local output
  if ! output="$(curl --http1.1 --retry 3 --retry-delay 1 --retry-all-errors -fsS "$@" 2>&1)"; then
    err "$label failed.

This local Enterprise E2E setup expects the quickstart baseline resources and
the mobile-specific helper resources to exist.
From a clean waltid-enterprise-quickstart checkout, run:
  Start Docker Desktop or another Docker daemon
  In config/enterprise.conf, use:
    baseDomain = "enterprise.localhost"
    baseSsl = true
    # basePort = 7500
  docker compose up
  cd cli && npm install
  HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --init-system
  HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --setup-all
  Then create the mobile helper resources explicitly:
    waltid-wallet-demo-android/scripts/e2e-local-enterprise.sh --prepare-only
    # or
    waltid-wallet-demo-ios/scripts/e2e-local-enterprise.sh --prepare-only

If the database already exists, rerun:
  cd cli && HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --setup-all
  Then rerun one platform script with --prepare-only if issuer2-noattest or
  verifier2-mobile is missing.

Original curl output:
$output"
  fi
  printf '%s' "$output"
}

get_enterprise_admin_token() {
  local api_url="${1:?api_url is required}"
  local admin_email="${2:?admin_email is required}"
  local admin_password="${3:?admin_password is required}"

  e2e_curl "Enterprise admin login at $api_url" \
    "$api_url/auth/account/emailpass" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$admin_email\",\"password\":\"$admin_password\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])"
}

check_ngrok_domain() {
  local host_alias_domain="${1:?host_alias_domain is required}"

  e2e_curl "ngrok domain check for https://$host_alias_domain" \
    -o /dev/null \
    -H "ngrok-skip-browser-warning: true" \
    "https://$host_alias_domain" >/dev/null
}

create_verifier_session_payload() {
  local payload_file="${1:?payload_file is required}"

  cat > "$payload_file" <<'JSON'
{
  "flow_type": "cross_device",
  "core_flow": {
    "dcql_query": {
      "credentials": [
        {
          "id": "my_mdl",
          "format": "mso_mdoc",
          "meta": {
            "doctype_value": "org.iso.18013.5.1.mDL"
          },
          "claims": [
            {
              "path": [
                "org.iso.18013.5.1",
                "family_name"
              ]
            },
            {
              "path": [
                "org.iso.18013.5.1",
                "given_name"
              ]
            }
          ]
        }
      ]
    }
  }
}
JSON
}

validate_public_mobile_url() {
  local label="${1:?label is required}"
  local expected_public_base_url="${2:?expected_public_base_url is required}"
  local actual_url="${3:?actual_url is required}"

  ACTUAL_URL="$actual_url" \
  EXPECTED_PUBLIC_BASE_URL="$expected_public_base_url" \
  LABEL="$label" \
    python3 - <<'PY'
import os
import sys

actual = os.environ["ACTUAL_URL"]
expected = os.environ["EXPECTED_PUBLIC_BASE_URL"].rstrip("/")
label = os.environ["LABEL"]

if actual.startswith(expected + "/"):
    raise SystemExit(0)

message = f"""{label} uses a non-public local URL:
  {actual}

Expected it to start with:
  {expected}/

The mobile apps run outside the quickstart Docker network. Credential offer and
verifier request URLs must therefore use the public ngrok HTTPS origin, not
enterprise.localhost and not :7500.

From a clean waltid-enterprise-quickstart checkout:
  1. In config/enterprise.conf, use:
       baseDomain = "enterprise.localhost"
       baseSsl = true
       # basePort = 7500
  2. Restart the stack:
       docker compose down
       docker compose up
  3. Provision with the tunnel domain so the host alias exists, without running
     the quickstart's built-in primary use case:
       cd cli
       npm install
       HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --init-system
       HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --setup-all

For an existing database, rerun the same command with --setup-all after the
config change. If URLs remain stale, rerun --init-system and --setup-all from a
clean database."""
print(message, file=sys.stderr)
raise SystemExit(1)
PY
}

validate_public_offer_response() {
  local response_json="${1:?response_json is required}"
  local expected_public_base_url="${2:?expected_public_base_url is required}"
  local offer_uri

  offer_uri="$(RESPONSE_JSON="$response_json" python3 - <<'PY'
import json
import os
import sys
import urllib.parse

response = json.loads(os.environ["RESPONSE_JSON"])
offer = response.get("credentialOffer")
if not offer:
    print("Missing credentialOffer in issuer response", file=sys.stderr)
    raise SystemExit(1)

query = urllib.parse.parse_qs(urllib.parse.urlparse(offer).query)
values = query.get("credential_offer_uri")
if not values:
    print(f"credentialOffer does not contain credential_offer_uri: {offer}", file=sys.stderr)
    raise SystemExit(1)

print(values[0])
PY
  )" || err "Issuer profile offer probe returned an invalid credentialOffer response."

  validate_public_mobile_url "Credential offer URI" "$expected_public_base_url" "$offer_uri" \
    || err "Issuer profile offer probe returned a credential offer that is not usable from mobile."
}

validate_public_verifier_response() {
  local response_json="${1:?response_json is required}"
  local expected_public_base_url="${2:?expected_public_base_url is required}"
  local urls_file

  urls_file="$(mktemp "${TMPDIR:-/tmp}/waltid-e2e-verifier-urls.XXXXXX")"
  RESPONSE_JSON="$response_json" python3 - <<'PY' > "$urls_file"
import json
import os
import sys
import urllib.parse

response = json.loads(os.environ["RESPONSE_JSON"])
urls = []

bootstrap = response.get("bootstrapAuthorizationRequestUrl")
if not bootstrap:
    print("Missing bootstrapAuthorizationRequestUrl in verifier response", file=sys.stderr)
    raise SystemExit(1)

bootstrap_query = urllib.parse.parse_qs(urllib.parse.urlparse(bootstrap).query)
request_uri = bootstrap_query.get("request_uri", [None])[0]
if not request_uri:
    print(f"bootstrapAuthorizationRequestUrl does not contain request_uri: {bootstrap}", file=sys.stderr)
    raise SystemExit(1)
urls.append(("Verifier request URI", request_uri))

full = response.get("fullAuthorizationRequestUrl")
if full:
    full_query = urllib.parse.parse_qs(urllib.parse.urlparse(full).query)
    response_uri = full_query.get("response_uri", [None])[0]
    if response_uri:
        urls.append(("Verifier response URI", response_uri))

for label, url in urls:
    print(label)
    print(url)
PY
  local status=$?
  if [ "$status" -ne 0 ]; then
    rm -f "$urls_file"
    err "Verifier session probe returned an invalid verifier response."
  fi

  while IFS= read -r label && IFS= read -r url; do
    validate_public_mobile_url "$label" "$expected_public_base_url" "$url" \
      || {
        rm -f "$urls_file"
        err "Verifier session probe returned a verifier URL that is not usable from mobile."
      }
  done < "$urls_file"

  rm -f "$urls_file"
}

preflight_local_enterprise_resources() {
  local api_url="${1:?api_url is required}"
  local public_base_url="${2:?public_base_url is required}"
  local token="${3:?token is required}"
  local issuer_profile="${4:?issuer_profile is required}"
  local verifier="${5:?verifier is required}"
  local host_header="${6:-${ORG}.enterprise.localhost}"
  local work_dir

  log "CHECK" "Validating local Enterprise resources ($TENANT_PATH)"

  e2e_curl "Document signer certificate lookup ($TENANT_PATH.x509-store.vical-doc-signer-cert)" \
    "$api_url/v1/$TENANT_PATH.x509-store.vical-doc-signer-cert/x509-store-api/certificates" \
    -H "Authorization: Bearer $token" \
    -H "Host: $host_header" >/dev/null

  e2e_curl "IACA certificate lookup ($TENANT_PATH.x509-store.vical-iaca-cert)" \
    "$api_url/v1/$TENANT_PATH.x509-store.vical-iaca-cert/x509-store-api/certificates" \
    -H "Authorization: Bearer $token" \
    -H "Host: $host_header" >/dev/null

  local offer_response
  offer_response="$(e2e_curl "Issuer profile offer probe ($TENANT_PATH.$issuer_profile)" \
    "$public_base_url/v2/$TENANT_PATH.$issuer_profile/issuer-service-api/credentials/offers" \
    -X POST \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -H "ngrok-skip-browser-warning: true" \
    -d '{"authMethod":"PRE_AUTHORIZED"}')"
  validate_public_offer_response "$offer_response" "$public_base_url"

  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/waltid-e2e-fixtures.XXXXXX")"
  create_verifier_session_payload "$work_dir/verifier-session.json"
  local verifier_response
  verifier_response="$(e2e_curl "Verifier session probe ($TENANT_PATH.$verifier)" \
    "$public_base_url/v1/$TENANT_PATH.$verifier/verifier2-service-api/verification-session/create" \
    -X POST \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -H "ngrok-skip-browser-warning: true" \
    -d @"$work_dir/verifier-session.json")"
  validate_public_verifier_response "$verifier_response" "$public_base_url"

  rm -rf "$work_dir"
}

ensure_non_attested_issuer2() {
  local token="${1:?token is required}"
  local host_header="${2:-${ORG}.enterprise.localhost}"
  local work_dir

  log "SETUP" "Ensuring non-attested issuer exists (issuer2-noattest)"

  curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.issuer2-noattest/resource-api/services/create" \
    -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" -H "Host: $host_header" \
    -d "{\"type\":\"issuer2\",\"_id\":\"$TENANT_PATH.issuer2-noattest\",\"tokenKeyId\":\"$TENANT_PATH.kms.issuer-signing-key\",\"kms\":\"$TENANT_PATH.kms\",\"credentialConfigurations\":{\"org.iso.18013.5.1.mDL\":{\"format\":\"mso_mdoc\",\"doctype\":\"org.iso.18013.5.1.mDL\",\"scope\":\"org.iso.18013.5.1.mDL\",\"credential_signing_alg_values_supported\":[-7,-9],\"cryptographic_binding_methods_supported\":[\"cose_key\"],\"proof_types_supported\":{\"jwt\":{\"proof_signing_alg_values_supported\":[\"ES256\"]}}}}}" \
    >/dev/null 2>&1 || true

  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/waltid-e2e-fixtures.XXXXXX")"

  e2e_curl "Document signer certificate lookup ($TENANT_PATH.x509-store.vical-doc-signer-cert)" \
    "http://localhost:$PORT/v1/$TENANT_PATH.x509-store.vical-doc-signer-cert/x509-store-api/certificates" \
    -H "Authorization: Bearer $token" -H "Host: $host_header" > "$work_dir/doc_cert.json"
  e2e_curl "IACA certificate lookup ($TENANT_PATH.x509-store.vical-iaca-cert)" \
    "http://localhost:$PORT/v1/$TENANT_PATH.x509-store.vical-iaca-cert/x509-store-api/certificates" \
    -H "Authorization: Bearer $token" -H "Host: $host_header" > "$work_dir/iaca_cert.json"

  DOC_CERT_JSON="$work_dir/doc_cert.json" \
  IACA_CERT_JSON="$work_dir/iaca_cert.json" \
  PROFILE_PAYLOAD_JSON="$work_dir/mdl-profile-payload.json" \
  TENANT_PATH="$TENANT_PATH" \
    python3 - <<'PY'
import json
import os

with open(os.environ["DOC_CERT_JSON"]) as f:
    doc_pem = json.load(f)["data"]["pem"]
with open(os.environ["IACA_CERT_JSON"]) as f:
    iaca_pem = json.load(f)["data"]["pem"]

tenant_path = os.environ["TENANT_PATH"]
payload = {
    "name": "mdl-profile",
    "credentialConfigurationId": "org.iso.18013.5.1.mDL",
    "issuerKeyId": f"{tenant_path}.kms.issuer-signing-key",
    "x5Chain": [
        {"type": "pem-encoded-x509-certificate-descriptor", "pemEncodedCertificate": doc_pem},
        {"type": "pem-encoded-x509-certificate-descriptor", "pemEncodedCertificate": iaca_pem},
    ],
    "credentialData": {
        "org.iso.18013.5.1": {
            "family_name": "Doe",
            "given_name": "John",
            "birth_date": "1990-01-01",
            "issue_date": "2024-01-01",
            "expiry_date": "2029-01-01",
            "issuing_country": "US",
            "issuing_authority": "Test DMV",
            "document_number": "DL123456789",
            "un_distinguishing_sign": "USA",
        }
    }
}

with open(os.environ["PROFILE_PAYLOAD_JSON"], "w") as f:
    json.dump(payload, f)
PY

  curl -sf "http://localhost:$PORT/v2/$TENANT_PATH.issuer2-noattest.mdl-profile/issuer-service-api/credentials/profiles" \
    -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" -H "Host: $host_header" \
    -d @"$work_dir/mdl-profile-payload.json" >/dev/null 2>&1 || true

  rm -rf "$work_dir"
  log "SETUP" "issuer2-noattest ready"
}

ensure_mobile_verifier2() {
  local token="${1:?token is required}"
  local public_base_url="${2:?public_base_url is required}"
  local verifier="${3:?verifier is required}"
  local host_header="${4:-${ORG}.enterprise.localhost}"
  local verifier_target="$TENANT_PATH.$verifier"
  local trust_registry_target="$TENANT_PATH.trust-registry"

  log "SETUP" "Ensuring mobile verifier exists ($verifier)"

  curl -sf "http://localhost:$PORT/v1/$verifier_target/resource-api/services/create" \
    -X POST \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -H "Host: $host_header" \
    -d "{\"type\":\"verifier2\",\"_id\":\"$verifier_target\",\"baseUrl\":\"$public_base_url\",\"clientId\":\"$verifier-client\"}" \
    >/dev/null 2>&1 || true

  curl -sf "http://localhost:$PORT/v1/$verifier_target/verifier2-service-api/dependencies/add" \
    -X POST \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: text/plain" \
    -H "Host: $host_header" \
    --data "$trust_registry_target" \
    >/dev/null 2>&1 || true

  log "SETUP" "$verifier ready"
}
