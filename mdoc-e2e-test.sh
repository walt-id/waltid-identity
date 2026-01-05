#!/bin/bash
set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration - can be overridden by environment variables
WALLET_API_URL="${WALLET_API_URL:-http://localhost:7001}"
ISSUER_API_URL="${ISSUER_API_URL:-http://localhost:7002}"
VERIFIER_API_URL="${VERIFIER_API_URL:-http://localhost:7003}"

# Test user credentials
TEST_EMAIL="${TEST_EMAIL:-user@email.com}"
TEST_PASSWORD="${TEST_PASSWORD:-password}"

# Temporary files
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

echo -e "${GREEN}=== mDoc End-to-End Test ===${NC}"
echo "Wallet API: $WALLET_API_URL"
echo "Issuer API: $ISSUER_API_URL"
echo "Verifier API: $VERIFIER_API_URL"
echo "Test Email: $TEST_EMAIL"
echo ""

# Helper function to make API calls
api_call() {
    local method=$1
    local url=$2
    local data="${3:-}"
    local token="${4:-}"
    local accept_header="${5:-application/json}"
    
    # Build curl command
    local curl_args=(
        -s
        -w "\n%{http_code}"
        -X "$method"
        -H "Content-Type: application/json"
        -H "accept: $accept_header"
    )
    
    if [ -n "$token" ]; then
        curl_args+=(-H "Authorization: Bearer $token")
    fi
    
    if [ -n "$data" ]; then
        curl_args+=(-d "$data")
    fi
    
    curl_args+=("$url")

    local response=$(curl "${curl_args[@]}")
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo "$body"
        return 0
    elif [ "$http_code" -eq 409 ]; then
        echo -e "${YELLOW}Error: Conflict${NC}" >&2
        echo "$body" >&2
        return 0
    else
        echo -e "${RED}Error: HTTP $http_code${NC}" >&2
        echo "$body" >&2
        return 1
    fi
}

# Helper function for verification API call with custom headers
verifier_api_call() {
    local method=$1
    local url=$2
    local data="${3:-}"
    
    local curl_args=(
        -s
        -w "\n%{http_code}"
        -X "$method"
        -H "Content-Type: application/json"
        -H "accept: text/plain"
        -H "authorizeBaseUrl: openid4vp://authorize"
        -H "responseMode: direct_post.jwt"
        -H "openId4VPProfile: ISO_18013_7_MDOC"
    )
    
    if [ -n "$data" ]; then
        curl_args+=(-d "$data")
    fi
    
    curl_args+=("$url")


    local response=$(curl "${curl_args[@]}")
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo "$body"
        return 0
    else
        echo -e "${RED}Error: HTTP $http_code${NC}" >&2
        echo "$body" >&2
        return 1
    fi
}

# Step 1: Register a new wallet user
echo -e "${YELLOW}Step 1: Registering wallet user...${NC}"
REGISTER_RESPONSE=$(api_call POST "$WALLET_API_URL/wallet-api/auth/register" \
    "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\",\"type\":\"email\",\"name\":\"test\"}") || {
    echo -e "${RED}Failed to register user${NC}"
}
echo -e "${GREEN}✓ User registered${NC}"

# Step 2: Login and get token
echo -e "${YELLOW}Step 2: Logging in...${NC}"
LOGIN_RESPONSE=$(api_call POST "$WALLET_API_URL/wallet-api/auth/login" \
    "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\",\"type\":\"email\"}") || {
    echo -e "${RED}Failed to login${NC}"
    exit 1
}

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
    echo -e "${RED}Failed to extract token from login response${NC}"
    echo "$LOGIN_RESPONSE"
    exit 1
fi
echo -e "${GREEN}✓ Logged in successfully${NC}"

# Step 3: Get wallet ID
echo -e "${YELLOW}Step 3: Getting wallet ID...${NC}"
WALLETS_RESPONSE=$(api_call GET "$WALLET_API_URL/wallet-api/wallet/accounts/wallets" "" "$TOKEN") || {
    echo -e "${RED}Failed to get wallets${NC}"
    exit 1
}

WALLET_ID=$(echo "$WALLETS_RESPONSE" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
if [ -z "$WALLET_ID" ]; then
    echo -e "${RED}Failed to extract wallet ID${NC}"
    echo "$WALLETS_RESPONSE"
    exit 1
fi
echo -e "${GREEN}✓ Wallet ID: $WALLET_ID${NC}"

# Step 4: Issue mdoc credential
echo -e "${YELLOW}Step 4: Issuing mdoc credential...${NC}"


IACA_CERT_ROOT="-----BEGIN CERTIFICATE-----\nMIIBtDCCAVmgAwIBAgIUAOXLkeu9penFRno6oDcOBgT1odYwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDYzOTQ0WhcNNDAwNTI5MDYzOTQ0WjAoMQswCQYDVQQGEwJBVDEZMBcGA1UEAwwQV2FsdGlkIFRlc3QgSUFDQTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABAZGrRN7Oeanhn7MOaGU6HhaCt8ZMySk/nRHefLbRq8lChr+PS6JqpCJ503sEvByXzPDgPsp0urKg/y0E+F7q9+jYTBfMB0GA1UdDgQWBBTxCn2nWMrE70qXb614U14BweY2azASBgNVHRMBAf8ECDAGAQH/AgEAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDSQAwRgIhAOM37BjC48KhsSlU6mdJwlTLrad9VzlXVKc1GmjoCNm1AiEAkFRJalpz62QCOby9l7Vkq0LAdWVKiFMd0DmSxjsdT2U=\n-----END CERTIFICATE-----\n"


DS_CERT="-----BEGIN CERTIFICATE-----\nMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M\n-----END CERTIFICATE-----\n"


MDOC_ISSUANCE_REQUEST=$(cat <<EOF
{
  "issuerKey": {
    "type": "jwk",
    "jwk": {
      "kty": "EC",
      "d": "-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s",
      "crv": "P-256",
      "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
      "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
      "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
    }
  },
  "credentialConfigurationId": "org.iso.18013.5.1.mDL",
  "mdocData": {
    "org.iso.18013.5.1": {
      "family_name": "Doe",
      "given_name": "John",
      "birth_date": "1986-03-22",
      "issue_date": "2019-10-20",
      "expiry_date": "2024-10-20",
      "issuing_country": "AT",
      "issuing_authority": "AT DMV",
      "document_number": "123456789",
      "portrait": [
        141,
        182,
        121,
        111,
        238,
        50,
        120,
        94,
        54,
        111,
        113,
        13,
        241,
        12,
        12
      ],
      "driving_privileges": [
        {
          "vehicle_category_code": "A",
          "issue_date": "2018-08-09",
          "expiry_date": "2024-10-20"
        },
        {
          "vehicle_category_code": "B",
          "issue_date": "2017-02-23",
          "expiry_date": "2024-10-20"
        }
      ],
      "un_distinguishing_sign": "AT"
    }
  },
  "x5Chain": [
    "$DS_CERT"
  ]
}
EOF
)

OFFER_URL=$(api_call POST "$ISSUER_API_URL/openid4vc/mdoc/issue" "$MDOC_ISSUANCE_REQUEST") || {
    echo -e "${RED}Failed to issue mdoc credential${NC}"
    exit 1
}
echo -e "${GREEN}✓ Credential offer URL received${NC}"
echo "  Offer URL: $OFFER_URL"

# Step 5: Claim credential in wallet
echo -e "${YELLOW}Step 5: Claiming credential in wallet...${NC}"
CREDENTIALS_RESPONSE=$(api_call POST "$WALLET_API_URL/wallet-api/wallet/$WALLET_ID/exchange/useOfferRequest" \
    "$OFFER_URL" "$TOKEN") || {
    echo -e "${RED}Failed to claim credential${NC}"
    exit 1
}

CREDENTIAL_ID=$(echo "$CREDENTIALS_RESPONSE" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
if [ -z "$CREDENTIAL_ID" ]; then
    echo -e "${RED}Failed to extract credential ID${NC}"
    echo "$CREDENTIALS_RESPONSE"
    exit 1
fi
echo -e "${GREEN}✓ Credential claimed${NC}"
echo "  Credential ID: $CREDENTIAL_ID"

# Step 6: Create verification session
echo -e "${YELLOW}Step 6: Creating verification session...${NC}"

# Presentation request (from VerifierApiExamples.mDLRequiredFieldsExample)
PRESENTATION_REQUEST=$(cat <<EOF
{
  "request_credentials": [
    {
      "id": "mDL-request",
      "input_descriptor": {
        "id": "org.iso.18013.5.1.mDL",
        "format": {
          "mso_mdoc": {
            "alg": ["ES256"]
          }
        },
        "constraints": {
          "fields": [
            {
              "path": ["\$['org.iso.18013.5.1']['family_name']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['given_name']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['birth_date']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['issue_date']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['expiry_date']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['issuing_country']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['issuing_authority']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['document_number']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['portrait']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['driving_privileges']"],
              "intent_to_retain": false
            },
            {
              "path": ["\$['org.iso.18013.5.1']['un_distinguishing_sign']"],
              "intent_to_retain": false
            }
          ],
          "limit_disclosure": "required"
        }
      }
    }
  ],
  "trusted_root_cas": [
    "$IACA_CERT_ROOT"
  ],
  "openid_profile": "ISO_18013_7_MDOC"
}
EOF
)

PRESENTATION_URL=$(verifier_api_call POST "$VERIFIER_API_URL/openid4vc/verify" "$PRESENTATION_REQUEST") || {
    echo -e "${RED}Failed to create verification session${NC}"
    exit 1
}

REQUEST_URI_PARAM=$(echo "$PRESENTATION_URL" | grep -o 'request_uri=[^&]*' | cut -d'=' -f2)
if [ -z "$REQUEST_URI_PARAM" ]; then
    echo -e "${RED}Failed to extract request_uri from presentation URL${NC}"
    echo "Presentation URL: $PRESENTATION_URL"
    exit 1
fi

SESSION_ID=$(echo "$REQUEST_URI_PARAM" | sed 's/.*request%2F//' | sed 's/&.*$//' | sed 's/%[0-9A-Fa-f][0-9A-Fa-f]//g')


echo -e "${GREEN}✓ Verification session created${NC}"
echo "  Presentation URL: $PRESENTATION_URL"
echo "  Session ID: $SESSION_ID"

# Step 7: Use presentation request in wallet
echo -e "${YELLOW}Step 7: Using presentation request in wallet...${NC}"
USE_PRESENTATION_REQUEST=$(cat <<EOF
{
  "presentationRequest": "$PRESENTATION_URL",
  "selectedCredentials": ["$CREDENTIAL_ID"]
}
EOF
)

api_call POST "$WALLET_API_URL/wallet-api/wallet/$WALLET_ID/exchange/usePresentationRequest" \
    "$USE_PRESENTATION_REQUEST" "$TOKEN" "application/json" > /dev/null || {
    echo -e "${RED}Failed to use presentation request${NC}"
    exit 1
}
echo -e "${GREEN}✓ Presentation request used${NC}"

# Step 8: Check verification result
echo -e "${YELLOW}Step 8: Checking verification result...${NC}"
sleep 2  # Give the verifier a moment to process

SESSION_INFO=$(api_call GET "$VERIFIER_API_URL/openid4vc/session/$SESSION_ID") || {
    echo -e "${RED}Failed to get session info${NC}"
    exit 1
}
echo "Session info: $SESSION_INFO"

VERIFICATION_RESULT=$(echo "$SESSION_INFO" | grep -o '"verificationResult":[^,}]*' | cut -d':' -f2 | tr -d ' ')
if [ "$VERIFICATION_RESULT" = "true" ]; then
    echo -e "${GREEN}✓ Verification successful!${NC}"
    echo ""
    echo -e "${GREEN}=== Test Completed Successfully ===${NC}"
    echo "All steps completed:"
    echo "  1. ✓ User registered"
    echo "  2. ✓ User logged in"
    echo "  3. ✓ Wallet ID retrieved"
    echo "  4. ✓ mDoc credential issued"
    echo "  5. ✓ Credential claimed in wallet"
    echo "  6. ✓ Verification session created"
    echo "  7. ✓ Presentation request used"
    echo "  8. ✓ Verification result: SUCCESS"
    exit 0
else
    echo -e "${RED}✗ Verification failed${NC}"
    echo "Session info: $SESSION_INFO"
    exit 1
fi

