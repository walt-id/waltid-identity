OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://61b7-2001-871-26a-9ab5-c5fa-edcc-fb02-7b0c.ngrok-free.app/openid4vci" \
OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID="photoID_credential_dc+sd-jwt" \
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"