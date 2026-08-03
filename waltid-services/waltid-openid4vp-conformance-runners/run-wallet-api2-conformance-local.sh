#!/usr/bin/env bash
set -euo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IDENTITY_ROOT="$(cd "$RUNNER_DIR/../.." && pwd)"
TRUSTSTORE="$RUNNER_DIR/build/conformance/conformance-truststore.jks"
TRUSTSTORE_PASSWORD="${CONFORMANCE_TRUSTSTORE_PASSWORD:-changeit}"
WALLET_TASK="${OPENID4VCI_WALLET_CONFORMANCE_WALLET_GRADLE_TASK:-:waltid-services:waltid-wallet-api2:run}"

# Wallet API2 is a separate JVM. Prepare the suite certificate before starting it
# so Java can trust the local Nginx endpoint used by wallet conformance tests.
OPENID4VCI_WALLET_CONFORMANCE_PREPARE_TLS_ONLY=true \
  "$RUNNER_DIR/run-wallet-conformance-local.sh"

JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=$TRUSTSTORE -Djavax.net.ssl.trustStorePassword=$TRUSTSTORE_PASSWORD -Djavax.net.ssl.trustStoreType=JKS"
export JAVA_TOOL_OPTIONS

echo "Starting Wallet API2 with the local conformance truststore: $TRUSTSTORE"
cd "$IDENTITY_ROOT"
exec ./gradlew "$WALLET_TASK"
