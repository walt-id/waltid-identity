#!/usr/bin/env bash
set -euo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$RUNNER_DIR/../../.." && pwd)"
COMPOSE_FILE="${CONFORMANCE_COMPOSE_FILE:-$RUNNER_DIR/docker-compose-walt.yml}"
BASE_TRUSTSTORE="$RUNNER_DIR/conformance-truststore.jks"
TRUSTSTORE="$RUNNER_DIR/build/conformance/conformance-truststore.jks"
CLIENT_ATTESTER_JWK_FILE="$RUNNER_DIR/src/test/resources/keys/attester-key.json"
CLIENT_ATTESTER_TRUST_ROOT="$RUNNER_DIR/src/test/resources/certs/root-ca.pem"
RESULT_XML="$RUNNER_DIR/build/test-results/test/TEST-id.walt.openid4vp.conformance.IssuerConformanceTests.xml"

CONFORMANCE_HOST="localhost.emobix.co.uk"
CONFORMANCE_PORT="8443"
ISSUER_PROXY_HOST="$CONFORMANCE_HOST"
ISSUER_PROXY_PORT="9443"
ISSUER_HOST_PORT="7005"
LOCAL_CREDENTIAL_ISSUER_URL="https://$ISSUER_PROXY_HOST:$ISSUER_PROXY_PORT/openid4vci"
TRUSTSTORE_ALIAS="${CONFORMANCE_TRUSTSTORE_ALIAS:-conformance-test-localhost}"
TRUSTSTORE_PASSWORD="${CONFORMANCE_TRUSTSTORE_PASSWORD:-changeit}"
CERT_FILE="${CONFORMANCE_LOCAL_CERT_FILE:-/tmp/conformance-nginx.crt}"
KEY_FILE="${CONFORMANCE_LOCAL_KEY_FILE:-/tmp/conformance-nginx.key}"

OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="${OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL:-$LOCAL_CREDENTIAL_ISSUER_URL}"
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL
export OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID="${OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID:-identity_credential}"
export OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID="${OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID:-org.iso.18013.5.1.mDL}"
export OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID="${OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID:-$OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID}"
export OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID="${OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID:-$OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID}"
export OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE="${OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE:-$CLIENT_ATTESTER_JWK_FILE}"
export OPENID4VCI_CONFORMANCE_MATRIX="${OPENID4VCI_CONFORMANCE_MATRIX:-all}"
export OPENID4VCI_CONFORMANCE_TIMEOUT_MINUTES="${OPENID4VCI_CONFORMANCE_TIMEOUT_MINUTES:-240}"
OPENID4VCI_CONFORMANCE_PRESET="${OPENID4VCI_CONFORMANCE_PRESET:-vci-client-attestation-dpop-simple-unsigned}"
BROWSER_AUTOMATION_DEFAULT="false"

clear_variant_filters() {
  unset OPENID4VCI_CONFORMANCE_VARIANTS
  unset OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES
  unset OPENID4VCI_CONFORMANCE_FILTER_FORMATS
  unset OPENID4VCI_CONFORMANCE_FILTER_GRANT_TYPES
  unset OPENID4VCI_CONFORMANCE_FILTER_FLOW_VARIANTS
  unset OPENID4VCI_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES
  unset OPENID4VCI_CONFORMANCE_FILTER_SENDER_CONSTRAINTS
  unset OPENID4VCI_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES
  unset OPENID4VCI_CONFORMANCE_FILTER_REQUEST_METHODS
  unset OPENID4VCI_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION
}

module_selection_can_require_browser_automation() {
  local raw_groups="${OPENID4VCI_CONFORMANCE_MODULE_GROUPS:-}"
  local raw_modules="${OPENID4VCI_CONFORMANCE_MODULES:-}"
  raw_groups="${raw_groups//[[:space:]]/}"
  raw_modules="${raw_modules//[[:space:]]/}"

  if [[ -z "$raw_groups" && -z "$raw_modules" ]]; then
    return 0
  fi

  local groups=",$raw_groups,"
  local modules=",$raw_modules,"
  local grant_types=",${OPENID4VCI_CONFORMANCE_FILTER_GRANT_TYPES:-},"

  case "$groups" in
    *,positive,*|*,fapi,*|*,all,*)
      return 0
      ;;
    *,negative,*)
      # Authorization-code negative tests still need a successful browser login
      # before the suite can send its malformed token or credential request.
      if [[ -z "${OPENID4VCI_CONFORMANCE_FILTER_GRANT_TYPES:-}" || "$grant_types" == *,authorization_code,* ]]; then
        return 0
      fi
      ;;
  esac

  case "$modules" in
    *,oid4vci-1_0-issuer-happy-flow-multiple-clients,*)
      return 0
      ;;
  esac

  return 1
}

case "$OPENID4VCI_CONFORMANCE_PRESET" in
  all-basic-plan)
    BROWSER_AUTOMATION_DEFAULT="true"
    export OPENID4VCI_CONFORMANCE_STATIC_TX_CODE="${OPENID4VCI_CONFORMANCE_STATIC_TX_CODE:-493536}"
    clear_variant_filters
    export OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES="vci"
    ;;
  vci-client-attestation-dpop-simple-unsigned-preauth)
    export OPENID4VCI_CONFORMANCE_STATIC_TX_CODE="${OPENID4VCI_CONFORMANCE_STATIC_TX_CODE:-493536}"
    clear_variant_filters
    export OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES="vci"
    export OPENID4VCI_CONFORMANCE_FILTER_GRANT_TYPES="pre_authorization_code"
    export OPENID4VCI_CONFORMANCE_FILTER_FLOW_VARIANTS="issuer_initiated"
    export OPENID4VCI_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES="client_attestation"
    export OPENID4VCI_CONFORMANCE_FILTER_SENDER_CONSTRAINTS="dpop"
    export OPENID4VCI_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES="simple"
    export OPENID4VCI_CONFORMANCE_FILTER_REQUEST_METHODS="unsigned"
    ;;
  vci-client-attestation-dpop-simple-unsigned)
    BROWSER_AUTOMATION_DEFAULT="true"
    export OPENID4VCI_CONFORMANCE_STATIC_TX_CODE="${OPENID4VCI_CONFORMANCE_STATIC_TX_CODE:-493536}"
    clear_variant_filters
    export OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES="vci"
    export OPENID4VCI_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES="client_attestation"
    export OPENID4VCI_CONFORMANCE_FILTER_SENDER_CONSTRAINTS="dpop"
    export OPENID4VCI_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES="simple"
    export OPENID4VCI_CONFORMANCE_FILTER_REQUEST_METHODS="unsigned"
    ;;
  vci-haip-client-attestation-dpop-simple-unsigned)
    BROWSER_AUTOMATION_DEFAULT="true"
    clear_variant_filters
    unset OPENID4VCI_CONFORMANCE_STATIC_TX_CODE
    export OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES="vci_haip"
    export OPENID4VCI_CONFORMANCE_FILTER_FORMATS="sd_jwt_vc,mdoc"
    export OPENID4VCI_CONFORMANCE_FILTER_GRANT_TYPES="authorization_code"
    export OPENID4VCI_CONFORMANCE_FILTER_FLOW_VARIANTS="issuer_initiated,wallet_initiated"
    export OPENID4VCI_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES="client_attestation"
    export OPENID4VCI_CONFORMANCE_FILTER_SENDER_CONSTRAINTS="dpop"
    export OPENID4VCI_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES="simple"
    export OPENID4VCI_CONFORMANCE_FILTER_REQUEST_METHODS="unsigned"
    export OPENID4VCI_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION="plain,encrypted"
    ;;
  custom)
    ;;
  *)
    echo "Unknown OPENID4VCI_CONFORMANCE_PRESET: $OPENID4VCI_CONFORMANCE_PRESET" >&2
    echo "Supported values: all-basic-plan, vci-client-attestation-dpop-simple-unsigned-preauth, vci-client-attestation-dpop-simple-unsigned, vci-haip-client-attestation-dpop-simple-unsigned, custom" >&2
    exit 1
    ;;
esac

if [[ -z "${OPENID4VCI_CONFORMANCE_MODULE_GROUPS+x}" && -z "${OPENID4VCI_CONFORMANCE_MODULES+x}" ]]; then
  export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="metadata,positive"
fi

if [[ -n "${OPENID4VCI_CONFORMANCE_VARIANT_ID:-}" ]]; then
  export OPENID4VCI_CONFORMANCE_VARIANTS="$OPENID4VCI_CONFORMANCE_VARIANT_ID"
fi

###############################################################################
# TEMPORARY UPSTREAM CONFORMANCE-SUITE EXCLUSION
#
# Exclude oid4vci-1_0-issuer-happy-flow-multiple-clients ONLY for the
# pre_authorization_code variant. After client 1 consumes its one-time code, the
# upstream module starts an authorization-endpoint flow for client 2 and then
# submits client 1's already-consumed pre-authorized code again. issuer2 correctly
# rejects that request with HTTP 400 invalid_grant. Allowing pre-authorized-code
# reuse in issuer2 would be a protocol and security regression, not a valid way
# to satisfy this test.
#
# Keep this exclusion until the upstream module either obtains a fresh credential
# offer/pre-authorized code for client 2 or declares that variant inapplicable.
# The authorization_code variant is valid and MUST remain enabled. The Kotlin
# runner therefore applies this rule to the module-and-grant combination, not to
# the module globally. This also works for matrices containing both grant types.
###############################################################################
export OPENID4VCI_CONFORMANCE_EXCLUDE_PREAUTH_MULTIPLE_CLIENTS="true"

if [[ -z "${OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION+x}" ]] && module_selection_can_require_browser_automation; then
  BROWSER_AUTOMATION_DEFAULT="true"
  echo "Browser automation defaulted to true because the selected issuer module set can require front-channel authorization."
fi

export OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION="${OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION:-$BROWSER_AUTOMATION_DEFAULT}"

case "${OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION,,}" in
  true|1|yes)
    export OPENID4VCI_CONFORMANCE_AUTH_USERNAME="${OPENID4VCI_CONFORMANCE_AUTH_USERNAME:-jane@walt.id}"
    export OPENID4VCI_CONFORMANCE_AUTH_PASSWORD="${OPENID4VCI_CONFORMANCE_AUTH_PASSWORD:-jane}"
    export OPENID4VCI_CONFORMANCE_AUTH_TIMEOUT_SECONDS="${OPENID4VCI_CONFORMANCE_AUTH_TIMEOUT_SECONDS:-90}"
    export PLAYWRIGHT_BROWSER="${PLAYWRIGHT_BROWSER:-chromium}"
    export PLAYWRIGHT_HEADLESS="${PLAYWRIGHT_HEADLESS:-true}"
    # --with-deps invokes sudo from the Gradle child process. Default to browser
    # installation only, as Gradle cannot safely answer an interactive sudo prompt.
    export PLAYWRIGHT_INSTALL_WITH_DEPS="${PLAYWRIGHT_INSTALL_WITH_DEPS:-false}"
    export OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT="${OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT:-true}"
    ;;
  false|0|no)
    ;;
  *)
    echo "Unsupported OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION: $OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION" >&2
    echo "Supported values: true, false" >&2
    exit 1
    ;;
esac

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command docker
require_command openssl
require_command keytool
require_command curl

load_optional_pem_file() {
  local file_env="$1"
  local target_env="$2"
  local file="${!file_env:-}"

  if [[ -z "$file" ]]; then
    return 0
  fi

  if [[ ! -f "$file" ]]; then
    echo "Cannot find PEM file configured by $file_env: $file" >&2
    exit 1
  fi

  printf -v "$target_env" '%s' "$(<"$file")"
  export "$target_env"
}

load_optional_pem_file \
  OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM_FILE \
  OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM
load_optional_pem_file \
  OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE \
  OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM

if [[ ",${OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES:-}," == *,vci_haip,* ]]; then
  if [[ -z "${OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM:-}" || -z "${OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM:-}" ]]; then
    echo "HAIP selected without both credential/status-list trust anchors."
    echo "Set OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM_FILE and OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE when running HAIP credential x5c/status-list checks."
  fi
fi

credential_issuer_metadata_url() {
  local issuer_url="${1%/}"
  local scheme
  local without_scheme
  local authority
  local path

  if [[ "$issuer_url" != http://* && "$issuer_url" != https://* ]]; then
    echo "Credential issuer URL must start with http:// or https://: $issuer_url" >&2
    return 1
  fi

  scheme="${issuer_url%%://*}"
  without_scheme="${issuer_url#*://}"
  authority="${without_scheme%%/*}"
  path="${without_scheme#"$authority"}"
  printf '%s://%s/.well-known/openid-credential-issuer%s\n' "$scheme" "$authority" "$path"
}

ISSUER_METADATA_URL="$(credential_issuer_metadata_url "$OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL")"

print_env() {
  local name="$1"
  echo "$name=${!name-<unset>}"
}

print_secret_env() {
  local name="$1"
  if [[ -n "${!name-}" ]]; then
    echo "$name=<redacted>"
  else
    echo "$name=<unset>"
  fi
}

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Cannot find compose file: $COMPOSE_FILE" >&2
  exit 1
fi

if [[ ! -f "$BASE_TRUSTSTORE" ]]; then
  echo "Cannot find base truststore: $BASE_TRUSTSTORE" >&2
  exit 1
fi

if [[ ! -f "$OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE" ]]; then
  echo "Cannot find client attester JWK/JWKS file: $OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE" >&2
  exit 1
fi

if [[ "$OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL" == "$LOCAL_CREDENTIAL_ISSUER_URL" ]]; then
  echo "Checking bare-metal issuer2 on http://127.0.0.1:$ISSUER_HOST_PORT..."
  if ! curl -sS --connect-timeout 3 --max-time 5 -o /dev/null "http://127.0.0.1:$ISSUER_HOST_PORT/"; then
    echo "issuer2 is not reachable on host port $ISSUER_HOST_PORT." >&2
    echo "Start issuer2 with webHost=0.0.0.0 and webPort=$ISSUER_HOST_PORT." >&2
    exit 1
  fi
fi

echo "Starting local OpenID conformance suite..."
docker compose -f "$COMPOSE_FILE" up -d --build

NGINX_ID="$(docker compose -f "$COMPOSE_FILE" ps -q nginx)"
if [[ -z "$NGINX_ID" ]]; then
  echo "Could not find running nginx container for conformance suite." >&2
  exit 1
fi

echo "Generating local conformance TLS certificate..."
openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
  -keyout "$KEY_FILE" \
  -out "$CERT_FILE" \
  -subj "/CN=$CONFORMANCE_HOST" \
  -addext "subjectAltName=DNS:$CONFORMANCE_HOST,DNS:localhost,IP:127.0.0.1" \
  >/dev/null 2>&1

echo "Installing certificate into nginx container..."
docker cp "$CERT_FILE" "$NGINX_ID":/etc/ssl/certs/nginx-selfsigned.crt
docker cp "$KEY_FILE" "$NGINX_ID":/etc/ssl/private/nginx-selfsigned.key
docker exec "$NGINX_ID" nginx -t
docker exec "$NGINX_ID" nginx -s reload

mkdir -p "$(dirname "$TRUSTSTORE")"
cp "$BASE_TRUSTSTORE" "$TRUSTSTORE"
export CONFORMANCE_TRUSTSTORE_PATH="$TRUSTSTORE"
export CONFORMANCE_TRUSTSTORE_PASSWORD="$TRUSTSTORE_PASSWORD"

echo "Importing the same certificate into the temporary Gradle test truststore..."
keytool -delete \
  -alias "$TRUSTSTORE_ALIAS" \
  -keystore "$TRUSTSTORE" \
  -storepass "$TRUSTSTORE_PASSWORD" \
  -noprompt >/dev/null 2>&1 || true

keytool -importcert \
  -alias "$TRUSTSTORE_ALIAS" \
  -file "$CERT_FILE" \
  -keystore "$TRUSTSTORE" \
  -storepass "$TRUSTSTORE_PASSWORD" \
  -noprompt

echo "Checking conformance suite health..."
for attempt in $(seq 1 60); do
  if curl -ksf "https://$CONFORMANCE_HOST:$CONFORMANCE_PORT/api/server" >/dev/null; then
    echo "Conformance suite is reachable at https://$CONFORMANCE_HOST:$CONFORMANCE_PORT"
    break
  fi

  if [[ "$attempt" == "60" ]]; then
    echo "Conformance suite did not become reachable at https://$CONFORMANCE_HOST:$CONFORMANCE_PORT/api/server" >&2
    echo "Check compose logs with:" >&2
    echo "docker compose -f \"$COMPOSE_FILE\" logs --tail=100 nginx server" >&2
    exit 1
  fi

  sleep 2
done

echo "Checking issuer metadata through the configured HTTPS endpoint..."
if ! curl -ksf --connect-timeout 5 --max-time 15 "$ISSUER_METADATA_URL" >/dev/null; then
  echo "Issuer metadata is not reachable at: $ISSUER_METADATA_URL" >&2
  echo "For local runs, configure issuer2 baseUrl as https://$ISSUER_PROXY_HOST:$ISSUER_PROXY_PORT." >&2
  exit 1
fi

SERVER_ID="$(docker compose -f "$COMPOSE_FILE" ps -q server)"
if [[ -z "$SERVER_ID" ]]; then
  echo "Could not find running server container for conformance suite." >&2
  exit 1
fi

echo "Checking issuer metadata from the conformance-suite container..."
if ! docker exec "$SERVER_ID" curl -ksf --connect-timeout 5 --max-time 15 "$ISSUER_METADATA_URL" >/dev/null; then
  echo "The conformance-suite container cannot reach: $ISSUER_METADATA_URL" >&2
  echo "Check the nginx network alias and proxy logs with:" >&2
  echo "docker compose -f \"$COMPOSE_FILE\" logs --tail=100 nginx server" >&2
  exit 1
fi

echo
echo "Running issuer conformance test with:"
echo "OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL=$OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL"
echo "OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID=$OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID"
echo "OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID=$OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID"
echo "OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID=$OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID"
echo "OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID=$OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID"
echo "OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE=$OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE"
echo "OPENID4VCI_CONFORMANCE_PRESET=$OPENID4VCI_CONFORMANCE_PRESET"
echo "OPENID4VCI_CONFORMANCE_MATRIX=$OPENID4VCI_CONFORMANCE_MATRIX"
echo "OPENID4VCI_CONFORMANCE_TIMEOUT_MINUTES=$OPENID4VCI_CONFORMANCE_TIMEOUT_MINUTES"
print_env OPENID4VCI_CONFORMANCE_VARIANTS
print_env OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES
print_env OPENID4VCI_CONFORMANCE_FILTER_FORMATS
print_env OPENID4VCI_CONFORMANCE_FILTER_GRANT_TYPES
print_env OPENID4VCI_CONFORMANCE_FILTER_FLOW_VARIANTS
print_env OPENID4VCI_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES
print_env OPENID4VCI_CONFORMANCE_FILTER_SENDER_CONSTRAINTS
print_env OPENID4VCI_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES
print_env OPENID4VCI_CONFORMANCE_FILTER_REQUEST_METHODS
print_env OPENID4VCI_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION
print_env OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM_FILE
print_env OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE
print_secret_env OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM
print_secret_env OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM
print_env OPENID4VCI_CONFORMANCE_MODULE_GROUPS
print_env OPENID4VCI_CONFORMANCE_MODULES
print_env OPENID4VCI_CONFORMANCE_EXCLUDE_PREAUTH_MULTIPLE_CLIENTS
print_env OPENID4VCI_CONFORMANCE_STATIC_TX_CODE
print_env OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION
print_env OPENID4VCI_CONFORMANCE_AUTH_USERNAME
print_secret_env OPENID4VCI_CONFORMANCE_AUTH_PASSWORD
print_env OPENID4VCI_CONFORMANCE_AUTH_TIMEOUT_SECONDS
print_env PLAYWRIGHT_BROWSER
print_env PLAYWRIGHT_HEADLESS
print_env PLAYWRIGHT_INSTALL_WITH_DEPS
print_env OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT
echo
echo "For the default client-attester JWK, configure issuer2 client attestation as x509-chain"
echo "and trust this root certificate:"
echo "$CLIENT_ATTESTER_TRUST_ROOT"
echo

cd "$REPO_ROOT"

PLAYWRIGHT_GRADLE_ARGS=(
  "-Pplaywright.browser=${PLAYWRIGHT_BROWSER:-chromium}"
  "-Pplaywright.installWithDeps=${PLAYWRIGHT_INSTALL_WITH_DEPS:-false}"
)
TEST_GRADLE_ARGS=(
  "-Dopenid4vci.conformance.browser-automation=$OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION"
)

case "${OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION,,}" in
  true|1|yes)
    TEST_GRADLE_ARGS+=(
      "-Dopenid4vci.conformance.auth-username=$OPENID4VCI_CONFORMANCE_AUTH_USERNAME"
      "-Dopenid4vci.conformance.auth-password=$OPENID4VCI_CONFORMANCE_AUTH_PASSWORD"
      "-Dopenid4vci.conformance.auth-timeout-seconds=$OPENID4VCI_CONFORMANCE_AUTH_TIMEOUT_SECONDS"
      "-Dplaywright.browser=$PLAYWRIGHT_BROWSER"
      "-Dplaywright.headless=$PLAYWRIGHT_HEADLESS"
    )
    ;;
esac

case "${OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION,,}" in
  true|1|yes)
    case "${OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT,,}" in
      true|1|yes)
        echo "Installing Playwright browser for authorization-code conformance tests..."
        ./gradlew "${PLAYWRIGHT_GRADLE_ARGS[@]}" \
          :waltid-services:waltid-openid4vp-conformance-runners:installPlaywrightBrowsers \
          --no-build-cache
        ;;
      false|0|no)
        echo "Skipping Playwright browser install because OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT=false"
        ;;
      *)
        echo "Unsupported OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT: $OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT" >&2
        echo "Supported values: true, false" >&2
        exit 1
        ;;
    esac
    ;;
esac

GRADLE_EXIT=0
set +e
./gradlew "${TEST_GRADLE_ARGS[@]}" \
  :waltid-services:waltid-openid4vp-conformance-runners:cleanTest \
  :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "id.walt.openid4vp.conformance.IssuerConformanceTests.runIssuerConformanceTests" \
  --rerun-tasks \
  --no-build-cache
GRADLE_EXIT=$?
set -e

echo
echo "Result summary:"
if [[ -f "$RESULT_XML" ]]; then
  grep -E 'tests=|skipped=|failures=|errors=' "$RESULT_XML"

  if grep -q 'skipped="1"' "$RESULT_XML"; then
    echo
    echo "Skip reason:"
    grep -o '<skipped[^>]*>' "$RESULT_XML" || true
  fi
else
  echo "No JUnit XML result found at: $RESULT_XML" >&2
fi

exit "$GRADLE_EXIT"
