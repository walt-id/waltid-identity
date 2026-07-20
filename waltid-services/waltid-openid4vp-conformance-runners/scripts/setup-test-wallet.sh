#!/bin/bash
# Setup test wallet with credentials for VP wallet conformance tests
# Prerequisites: wallet-api2 (port 7005) and issuer-api2 (port 7002) must be running

set -e

WALLET_API="http://127.0.0.1:7005"
ISSUER_API="http://127.0.0.1:7002"

# Wallet-controlled verifier trust anchor. The verifier's Request Object x5c
# contains only its leaf certificate; this root is provisioned out of band.
VERIFIER_CA_PEM='-----BEGIN CERTIFICATE-----
MIIBlzCCAT2gAwIBAgIUUffF2b0tyOxgDu7q+kMpwY3pfNUwCgYIKoZIzj0EAwIw
MDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5p
ZDAeFw0yNjA1MTkwNDA4MTZaFw0zNjA1MTYwNDA4MTZaMDAxHDAaBgNVBAMME3dh
bHQuaWQgVmVyaWZpZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIB
BggqhkjOPQMBBwNCAAQnFYwN1ypusrveHnOwC2ZFBT6PosWX5l1caoRPoziV8jn8
EJx0uKD5RHC0p1CbYGHBqE74YUw7xlydTT1jXfCsozUwMzASBgNVHRMBAf8ECDAG
AQH/AgEAMB0GA1UdDgQWBBRdho/7KlGi74YmeLFqLMfbH6cSkzAKBggqhkjOPQQD
AgNIADBFAiEAudxJV83uP0g5zLXI85ExlkRMKZI52mkBkk074ST2KPACIEsFnJDr
xtEgGXjHNMaUj7FOpC4tJyGlg2DSpXSOlCkl
-----END CERTIFICATE-----'

echo "=== VP Wallet Conformance Test Setup ==="
echo ""

# Check services are running
echo "Checking services..."
if ! curl -sf "$WALLET_API/livez" > /dev/null 2>&1; then
    echo "ERROR: Wallet API not running on $WALLET_API"
    echo "Start it with: ./gradlew :waltid-services:waltid-wallet-api2:run"
    exit 1
fi
if ! curl -sf "$ISSUER_API/livez" > /dev/null 2>&1; then
    echo "ERROR: Issuer API not running on $ISSUER_API"
    echo "Start it with: ./gradlew :waltid-services:waltid-issuer-api2:run"
    exit 1
fi
echo "✓ Both services are running"
echo ""

# Create credential store
echo "Creating credential store..."
STORE_RESULT=$(curl -sf -X POST "$WALLET_API/stores/credentials/conformance-store" 2>/dev/null || echo "exists")
if [ "$STORE_RESULT" = "exists" ]; then
    echo "✓ Credential store already exists"
else
    echo "✓ Credential store created"
fi

# Create wallet with credential store
echo "Creating wallet..."
WALLET_CONFIG=$(jq -n --arg verifierCa "$VERIFIER_CA_PEM" '{
  credentialStoreIds: ["conformance-store"],
  requestObjectX509Trust: {
    trustAnchorPemCertificates: [$verifierCa],
    allowedRequestObjectAlgorithms: ["ES256"],
    requireTrustAnchorOmittedFromX5c: true,
    rejectLeafTrustAnchor: true
  }
}')
WALLET_ID=$(curl -sf -X POST "$WALLET_API/wallet" \
    -H "Content-Type: application/json" \
    -d "$WALLET_CONFIG" | jq -r '.walletId')
echo "✓ Created wallet: $WALLET_ID"

# Generate key for the wallet
echo "Generating key..."
KEY_ID=$(curl -sf -X POST "$WALLET_API/wallet/$WALLET_ID/keys/generate" \
    -H "Content-Type: application/json" \
    -d '{"keyType": "secp256r1"}' | jq -r '.keyId')
echo "✓ Generated key (secp256r1)"

# Create DID (internal use only)
DID=$(curl -sf -X POST "$WALLET_API/wallet/$WALLET_ID/dids/create" \
    -H "Content-Type: application/json" \
    -d '{"method": "key"}' | jq -r '.did')
echo "✓ Created holder DID"

# Create credential offer
echo "Creating credential offer..."
OFFER=$(curl -sf -X POST "$ISSUER_API/issuer2/credential-offers" \
    -H "Content-Type: application/json" \
    -d '{
      "profileId": "identityCredentialSdJwt",
      "authMethod": "PRE_AUTHORIZED",
      "valueMode": "BY_VALUE",
      "expiresInSeconds": 3600
    }' | jq -r '.credentialOffer')
echo "✓ Credential offer created"

# Receive credential
echo "Receiving credential..."
CRED_RESULT=$(curl -sf -X POST "$WALLET_API/wallet/$WALLET_ID/credentials/receive" \
    -H "Content-Type: application/json" \
    -d "{\"offerUrl\": \"$OFFER\"}")
CRED_IDS=$(echo "$CRED_RESULT" | jq -r '.credentialIds[]')
echo "✓ Received credential: $CRED_IDS"

# Verify credential details
echo ""
echo "=== Verifying Credential ==="
CRED_ID=$(curl -sf "$WALLET_API/wallet/$WALLET_ID/credentials" | jq -r '.[0].id')
CRED_DATA=$(curl -sf "$WALLET_API/wallet/$WALLET_ID/credentials/$CRED_ID")
SIGNED=$(echo "$CRED_DATA" | jq -r '.credential.signed')
JWT_HEADER=$(echo "$SIGNED" | cut -d'.' -f1 | base64 -d 2>/dev/null)

# Extract credential info
VCT=$(echo "$CRED_DATA" | jq -r '.credential.credentialData.vct')
CRED_TYPE=$(echo "$CRED_DATA" | jq -r '.credential.type')
FORMAT=$(echo "$CRED_DATA" | jq -r '.credential.format')

# Check x5c in JWT header (HAIP compliance)
HAS_X5C=$(echo "$JWT_HEADER" | jq -r 'has("x5c")')
HAS_KID=$(echo "$JWT_HEADER" | jq -r 'has("kid")')
ALG=$(echo "$JWT_HEADER" | jq -r '.alg')
TYP=$(echo "$JWT_HEADER" | jq -r '.typ')

echo "Format: $FORMAT"
echo "Type: $CRED_TYPE"
echo "VCT: $VCT"
echo ""
echo "JWT Header:"
echo "  alg: $ALG"
echo "  typ: $TYP"
if [ "$HAS_X5C" = "true" ]; then
    X5C_COUNT=$(echo "$JWT_HEADER" | jq '.x5c | length')
    echo "  x5c: ✓ Present ($X5C_COUNT certificates in chain)"
else
    echo "  x5c: ✗ Missing (HAIP non-compliant!)"
fi
if [ "$HAS_KID" = "true" ]; then
    KID_VAL=$(echo "$JWT_HEADER" | jq -r '.kid')
    echo "  kid: $KID_VAL"
else
    echo "  kid: (not present - correct for x5c signing)"
fi

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Wallet ID: $WALLET_ID"
echo ""

# Output wallet ID for use in tests
echo "$WALLET_ID" > /tmp/conformance-wallet-id.txt
echo "Wallet ID saved to /tmp/conformance-wallet-id.txt"

echo ""
echo "To run conformance tests:"
echo "  ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \\"
echo "      --tests \"VpWalletConformanceTests\" --rerun-tasks"
