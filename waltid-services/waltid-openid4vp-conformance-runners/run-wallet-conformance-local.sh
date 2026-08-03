#!/usr/bin/env bash
set -euo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IDENTITY_ROOT="$(cd "$RUNNER_DIR/../.." && pwd)"
COMPOSE_FILE="${CONFORMANCE_COMPOSE_FILE:-$RUNNER_DIR/docker-compose-walt.yml}"
BASE_TRUSTSTORE="$RUNNER_DIR/conformance-truststore.jks"
TRUSTSTORE="$RUNNER_DIR/build/conformance/conformance-truststore.jks"
RESULT_XML="$RUNNER_DIR/build/test-results/test/TEST-id.walt.openid4vp.conformance.VciWalletConformanceTests.xml"

CONFORMANCE_HOST="${OPENID4VCI_WALLET_CONFORMANCE_HOST:-localhost.emobix.co.uk}"
CONFORMANCE_PORT="${OPENID4VCI_WALLET_CONFORMANCE_PORT:-8443}"
WALLET_API_URL="${OPENID4VCI_WALLET_CONFORMANCE_WALLET_URL:-http://127.0.0.1:7006}"
ADAPTER_PORT="${OPENID4VCI_WALLET_CONFORMANCE_ADAPTER_PORT:-7007}"
ADAPTER_PUBLIC_URL="${OPENID4VCI_WALLET_CONFORMANCE_ADAPTER_PUBLIC_URL:-https://localhost.emobix.co.uk:9444}"
TRUSTSTORE_ALIAS="${CONFORMANCE_TRUSTSTORE_ALIAS:-conformance-test-localhost}"
TRUSTSTORE_PASSWORD="${CONFORMANCE_TRUSTSTORE_PASSWORD:-changeit}"
TLS_DIR="$RUNNER_DIR/build/conformance"
CERT_FILE="${CONFORMANCE_LOCAL_CERT_FILE:-$TLS_DIR/nginx.crt}"
KEY_FILE="${CONFORMANCE_LOCAL_KEY_FILE:-$TLS_DIR/nginx.key}"
PREPARE_TLS_ONLY="${OPENID4VCI_WALLET_CONFORMANCE_PREPARE_TLS_ONLY:-false}"

export OPENID4VCI_WALLET_CONFORMANCE_HOST="$CONFORMANCE_HOST"
export OPENID4VCI_WALLET_CONFORMANCE_PORT="$CONFORMANCE_PORT"
export OPENID4VCI_WALLET_CONFORMANCE_WALLET_URL="$WALLET_API_URL"
export OPENID4VCI_WALLET_CONFORMANCE_ADAPTER_PORT="$ADAPTER_PORT"
export OPENID4VCI_WALLET_CONFORMANCE_ADAPTER_PUBLIC_URL="$ADAPTER_PUBLIC_URL"
export OPENID4VCI_WALLET_CONFORMANCE_TX_CODE="${OPENID4VCI_WALLET_CONFORMANCE_TX_CODE:-123456}"
export OPENID4VCI_WALLET_CONFORMANCE_TIMEOUT_MINUTES="${OPENID4VCI_WALLET_CONFORMANCE_TIMEOUT_MINUTES:-1440}"
export OPENID4VCI_WALLET_CONFORMANCE_MODULE_TIMEOUT_MINUTES="${OPENID4VCI_WALLET_CONFORMANCE_MODULE_TIMEOUT_MINUTES:-5}"
export OPENID4VCI_WALLET_CONFORMANCE_STRICT="${OPENID4VCI_WALLET_CONFORMANCE_STRICT:-true}"

clear_variant_filters() {
  unset OPENID4VCI_WALLET_CONFORMANCE_VARIANTS
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_FAPI_PROFILES
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_FORMATS
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_GRANT_TYPES
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_FLOW_VARIANTS
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_SENDER_CONSTRAINTS
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_REQUEST_METHODS
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_ISSUANCE_MODES
  unset OPENID4VCI_WALLET_CONFORMANCE_FILTER_OFFER_VARIANTS
}

PRESET="${OPENID4VCI_WALLET_CONFORMANCE_PRESET:-baseline}"
export OPENID4VCI_WALLET_CONFORMANCE_PRESET="$PRESET"
case "$PRESET" in
  baseline|vci-sdjwt-authcode-issuer-private-key-jwt-dpop-simple-unsigned-immediate-plain)
    clear_variant_filters
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_FAPI_PROFILES="vci"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_FORMATS="sd_jwt_vc"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_GRANT_TYPES="authorization_code"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_FLOW_VARIANTS="issuer_initiated"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES="private_key_jwt"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_SENDER_CONSTRAINTS="dpop"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES="simple"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_REQUEST_METHODS="unsigned"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION="plain"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_ISSUANCE_MODES="immediate"
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_OFFER_VARIANTS="by_value"
    ;;
  all-basic-plan)
    clear_variant_filters
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_FAPI_PROFILES="vci"
    ;;
  all-haip-plan)
    clear_variant_filters
    export OPENID4VCI_WALLET_CONFORMANCE_FILTER_FAPI_PROFILES="vci_haip"
    ;;
  all)
    clear_variant_filters
    ;;
  custom)
    ;;
  *)
    echo "Unknown OPENID4VCI_WALLET_CONFORMANCE_PRESET: $PRESET" >&2
    echo "Supported values: baseline, all-basic-plan, all-haip-plan, all, custom" >&2
    exit 1
    ;;
esac

if [[ -z "${OPENID4VCI_WALLET_CONFORMANCE_MODULE_GROUPS+x}" && -z "${OPENID4VCI_WALLET_CONFORMANCE_MODULES+x}" ]]; then
  export OPENID4VCI_WALLET_CONFORMANCE_MODULE_GROUPS="all"
fi

export OPENID4VCI_WALLET_CONFORMANCE_BROWSER_AUTOMATION="${OPENID4VCI_WALLET_CONFORMANCE_BROWSER_AUTOMATION:-true}"
export OPENID4VCI_WALLET_CONFORMANCE_BROWSER_TIMEOUT_SECONDS="${OPENID4VCI_WALLET_CONFORMANCE_BROWSER_TIMEOUT_SECONDS:-90}"
export PLAYWRIGHT_BROWSER="${PLAYWRIGHT_BROWSER:-chromium}"
export PLAYWRIGHT_HEADLESS="${PLAYWRIGHT_HEADLESS:-true}"
# Gradle cannot provide an interactive sudo password. Install OS dependencies
# once outside Gradle when needed, then leave this false for repeatable runs.
export PLAYWRIGHT_INSTALL_WITH_DEPS="${PLAYWRIGHT_INSTALL_WITH_DEPS:-false}"
export OPENID4VCI_WALLET_CONFORMANCE_INSTALL_PLAYWRIGHT="${OPENID4VCI_WALLET_CONFORMANCE_INSTALL_PLAYWRIGHT:-true}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }
}

for command in docker openssl keytool curl; do
  require_command "$command"
done

[[ -f "$COMPOSE_FILE" ]] || { echo "Cannot find compose file: $COMPOSE_FILE" >&2; exit 1; }
[[ -f "$BASE_TRUSTSTORE" ]] || { echo "Cannot find base truststore: $BASE_TRUSTSTORE" >&2; exit 1; }

echo "Starting local OpenID conformance suite..."
docker compose -f "$COMPOSE_FILE" up -d --build
NGINX_ID="$(docker compose -f "$COMPOSE_FILE" ps -q nginx)"
[[ -n "$NGINX_ID" ]] || { echo "Could not find the nginx container." >&2; exit 1; }

mkdir -p "$TLS_DIR"
if [[ ! -s "$CERT_FILE" || ! -s "$KEY_FILE" ]]; then
  echo "Generating local conformance TLS certificate..."
  openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
    -keyout "$KEY_FILE" \
    -out "$CERT_FILE" \
    -subj "/CN=$CONFORMANCE_HOST" \
    -addext "subjectAltName=DNS:$CONFORMANCE_HOST,DNS:localhost,IP:127.0.0.1" \
    >/dev/null 2>&1
else
  echo "Reusing local conformance TLS certificate: $CERT_FILE"
fi
docker cp "$CERT_FILE" "$NGINX_ID":/etc/ssl/certs/nginx-selfsigned.crt
docker cp "$KEY_FILE" "$NGINX_ID":/etc/ssl/private/nginx-selfsigned.key
docker exec "$NGINX_ID" nginx -t
docker exec "$NGINX_ID" nginx -s reload

mkdir -p "$(dirname "$TRUSTSTORE")"
cp "$BASE_TRUSTSTORE" "$TRUSTSTORE"
export CONFORMANCE_TRUSTSTORE_PATH="$TRUSTSTORE"
export CONFORMANCE_TRUSTSTORE_PASSWORD="$TRUSTSTORE_PASSWORD"
keytool -delete -alias "$TRUSTSTORE_ALIAS" -keystore "$TRUSTSTORE" -storepass "$TRUSTSTORE_PASSWORD" -noprompt >/dev/null 2>&1 || true
keytool -importcert -alias "$TRUSTSTORE_ALIAS" -file "$CERT_FILE" -keystore "$TRUSTSTORE" -storepass "$TRUSTSTORE_PASSWORD" -noprompt

echo "Checking conformance suite health..."
for attempt in $(seq 1 60); do
  if curl -ksf "https://$CONFORMANCE_HOST:$CONFORMANCE_PORT/api/server" >/dev/null; then
    break
  fi
  [[ "$attempt" == "60" ]] && { echo "Conformance suite did not become reachable." >&2; exit 1; }
  sleep 2
done

if [[ "$PREPARE_TLS_ONLY" =~ ^(true|1|yes)$ ]]; then
  echo
  echo "Wallet conformance TLS is ready. Start Wallet API2 with:"
  echo "JAVA_TOOL_OPTIONS=\"-Djavax.net.ssl.trustStore=$TRUSTSTORE -Djavax.net.ssl.trustStorePassword=$TRUSTSTORE_PASSWORD -Djavax.net.ssl.trustStoreType=JKS\" ./gradlew :waltid-services:waltid-wallet-api2:run"
  exit 0
fi

echo "Checking Wallet API2 at $WALLET_API_URL..."
curl -fsS --connect-timeout 3 --max-time 10 "$WALLET_API_URL/wallet" >/dev/null || {
  echo "Wallet API2 is not reachable. Start it with:" >&2
  echo "JAVA_TOOL_OPTIONS=\"-Djavax.net.ssl.trustStore=$TRUSTSTORE -Djavax.net.ssl.trustStorePassword=$TRUSTSTORE_PASSWORD -Djavax.net.ssl.trustStoreType=JKS\" ./gradlew :waltid-services:waltid-wallet-api2:run" >&2
  exit 1
}

echo
echo "Wallet conformance configuration:"
for variable in \
  OPENID4VCI_WALLET_CONFORMANCE_WALLET_URL \
  OPENID4VCI_WALLET_CONFORMANCE_ADAPTER_PORT \
  OPENID4VCI_WALLET_CONFORMANCE_ADAPTER_PUBLIC_URL \
  OPENID4VCI_WALLET_CONFORMANCE_PRESET \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_FAPI_PROFILES \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_FORMATS \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_GRANT_TYPES \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_FLOW_VARIANTS \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_SENDER_CONSTRAINTS \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_REQUEST_METHODS \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_ISSUANCE_MODES \
  OPENID4VCI_WALLET_CONFORMANCE_FILTER_OFFER_VARIANTS \
  OPENID4VCI_WALLET_CONFORMANCE_MODULE_GROUPS \
  OPENID4VCI_WALLET_CONFORMANCE_MODULES \
  OPENID4VCI_WALLET_CONFORMANCE_BROWSER_AUTOMATION; do
  echo "$variable=${!variable-<unset>}"
done
echo

cd "$IDENTITY_ROOT"
if [[ "$OPENID4VCI_WALLET_CONFORMANCE_INSTALL_PLAYWRIGHT" =~ ^(true|1|yes)$ ]]; then
  ./gradlew \
    "-Pplaywright.browser=$PLAYWRIGHT_BROWSER" \
    "-Pplaywright.installWithDeps=$PLAYWRIGHT_INSTALL_WITH_DEPS" \
    :waltid-services:waltid-openid4vp-conformance-runners:installPlaywrightBrowsers \
    --no-build-cache
fi

set +e
./gradlew \
  "-Dplaywright.browser=$PLAYWRIGHT_BROWSER" \
  "-Dplaywright.headless=$PLAYWRIGHT_HEADLESS" \
  :waltid-services:waltid-openid4vp-conformance-runners:cleanTest \
  :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "id.walt.openid4vp.conformance.VciWalletConformanceTests.runWalletConformanceTests" \
  --rerun-tasks \
  --no-build-cache
GRADLE_EXIT=$?
set -e

echo
echo "Result summary:"
[[ -f "$RESULT_XML" ]] && grep -E 'tests=|skipped=|failures=|errors=' "$RESULT_XML" || true
exit "$GRADLE_EXIT"
