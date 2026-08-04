#!/usr/bin/env bash
set -euo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IDENTITY_ROOT="$(cd "$RUNNER_DIR/../.." && pwd)"
TRUSTSTORE="$RUNNER_DIR/build/conformance/conformance-truststore.jks"
TRUSTSTORE_PASSWORD="${CONFORMANCE_TRUSTSTORE_PASSWORD:-changeit}"
CONFORMANCE_WALLET_CONFIG="$RUNNER_DIR/config/conformance-wallet-service.conf"
WALLET_TASK="${OPENID4VCI_WALLET_CONFORMANCE_WALLET_GRADLE_TASK:-:waltid-services:waltid-wallet-api2:run}"

# Wallet API2 is a separate JVM. Prepare the suite certificate before starting it
# so Java can trust the local Nginx endpoint used by wallet conformance tests.
OPENID4VCI_WALLET_CONFORMANCE_PREPARE_TLS_ONLY=true \
  "$RUNNER_DIR/run-wallet-conformance-local.sh"

[[ -f "$CONFORMANCE_WALLET_CONFIG" ]] || { echo "Cannot find Wallet API2 conformance config: $CONFORMANCE_WALLET_CONFIG" >&2; exit 1; }

JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=$TRUSTSTORE -Djavax.net.ssl.trustStorePassword=$TRUSTSTORE_PASSWORD -Djavax.net.ssl.trustStoreType=JKS -Dconfig.file.wallet-service=$CONFORMANCE_WALLET_CONFIG"
export JAVA_TOOL_OPTIONS

echo "Starting Wallet API2 with the conformance truststore and test-only attester config"
cd "$IDENTITY_ROOT"
exec ./gradlew "$WALLET_TASK"
