# EUDI Wallet Integration Guide

This guide explains how to configure the walt.id issuer to work with the EUDI Reference Wallet and other OpenID4VCI Draft 13+ compliant wallets.

## Prerequisites

- Java 21+
- Docker and Docker Compose
- Git
- Basic understanding of OpenID4VCI and credential formats

## Supported Credential Formats

| Credential | Config ID | Format | VCT/DocType |
|------------|-----------|--------|-------------|
| EUDI PID (mDoc) | `eu.europa.ec.eudi.pid.1` | `mso_mdoc` | `eu.europa.ec.eudi.pid.1` |
| EUDI PID (SD-JWT) | `eu.europa.ec.eudi.pid_vc_sd_jwt` | `dc+sd-jwt` | `urn:eudi:pid:1` |
| Mobile Driving License | `org.iso.18013.5.1.mDL` | `mso_mdoc` | `org.iso.18013.5.1.mDL` |

## Quick Start

### 1. Build the Custom Issuer Image

EUDI wallet support requires custom-built Docker images with Draft 13+ protocol fixes:

```bash
# Build the issuer API image
./gradlew :waltid-services:waltid-issuer-api:jibDockerBuild

# Tag to match docker-compose VERSION_TAG (default: stable)
docker tag waltid/issuer-api:latest waltid/issuer-api:stable

# Start the services
cd docker-compose
docker compose --profile identity up -d --force-recreate issuer-api
```

### 2. Configure Credential Metadata

Edit `docker-compose/issuer-api/config/credential-issuer-metadata.conf`:

```hocon
# EUDI PID in mDoc format
"eu.europa.ec.eudi.pid.1" {
  format = "mso_mdoc"
  doctype = "eu.europa.ec.eudi.pid.1"
  cryptographic_binding_methods_supported = ["cose_key"]
  proof_types_supported = {
    jwt = {
      proof_signing_alg_values_supported = ["ES256"]
    }
  }
  display = [{
    name = "EU Digital Identity"
    locale = "en"
    logo = {
      url = "https://example.com/pid-logo.png"
    }
  }]
}

# EUDI PID in SD-JWT format
"eu.europa.ec.eudi.pid_vc_sd_jwt" {
  format = "dc+sd-jwt"  # NOT vc+sd-jwt
  vct = "urn:eudi:pid:1"
  cryptographic_binding_methods_supported = ["jwk"]
  proof_types_supported = {
    jwt = {
      proof_signing_alg_values_supported = ["ES256"]
    }
  }
}

# Mobile Driving License
"org.iso.18013.5.1.mDL" {
  format = "mso_mdoc"
  doctype = "org.iso.18013.5.1.mDL"
  cryptographic_binding_methods_supported = ["cose_key"]
  proof_types_supported = {
    jwt = {
      proof_signing_alg_values_supported = ["ES256"]
    }
  }
}
```

## API Usage

### Issue a PID Credential

```bash
curl -X POST http://localhost:7002/openid4vc/mdoc/issue \
  -H "Content-Type: application/json" \
  -d '{
    "issuerKey": {
      "type": "jwk",
      "jwk": { ... }
    },
    "issuerDid": "did:key:...",
    "credentialConfigurationId": "eu.europa.ec.eudi.pid.1",
    "credentialData": {
      "family_name": "DOE",
      "given_name": "JOHN",
      "birth_date": "1990-01-15",
      "issuing_country": "DE",
      "issuing_authority": "German Government"
    }
  }'
```

Response:
```
openid-credential-offer://?credential_offer_uri=https://issuer.example.com/openid4vc/draft13/credentialOffer?id=...
```

### Issue an SD-JWT PID

```bash
curl -X POST http://localhost:7002/openid4vc/sdjwt/issue \
  -H "Content-Type: application/json" \
  -d '{
    "issuerKey": { ... },
    "issuerDid": "did:key:...",
    "credentialConfigurationId": "eu.europa.ec.eudi.pid_vc_sd_jwt",
    "credentialData": {
      "family_name": "DOE",
      "given_name": "JOHN",
      "birth_date": "1990-01-15"
    }
  }'
```

## Protocol Requirements

### Draft 13+ Credential Requests

EUDI wallets use Draft 13+ format with `credential_configuration_id` instead of `format`:

```json
{
  "credential_configuration_id": "eu.europa.ec.eudi.pid.1",
  "proofs": {
    "jwt": ["eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0In0..."]
  }
}
```

### JWT Proofs

The wallet sends JWT proofs with the following structure:

**Header:**
```json
{
  "alg": "ES256",
  "typ": "openid4vci-proof+jwt",
  "jwk": {
    "kty": "EC",
    "crv": "P-256",
    "x": "...",
    "y": "..."
  }
}
```

**Payload:**
```json
{
  "iss": "https://wallet.example.org",
  "aud": "https://issuer.example.com",
  "iat": 1704067200,
  "nonce": "c_nonce_from_token_response"
}
```

### DPoP Support (Optional)

The issuer supports DPoP (RFC 9449) for enhanced security. Wallets can send DPoP proofs in the token request:

```http
POST /token HTTP/1.1
DPoP: eyJhbGciOiJFUzI1NiIsInR5cCI6ImRwb3Arand0Iiwiandrill...
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:pre-authorized_code&
pre-authorized_code=...
```

## Common Issues

### "Unsupported credential format"

**Cause:** Using `vc+sd-jwt` instead of `dc+sd-jwt` for SD-JWT credentials.

**Solution:** Update credential configuration to use `format = "dc+sd-jwt"`.

### "Invalid credential_configuration_id"

**Cause:** The credential configuration ID in the request doesn't match any defined in metadata.

**Solution:** Ensure the `credentialConfigurationId` in your issuance request matches exactly what's defined in `credential-issuer-metadata.conf`.

### "Proof verification failed"

**Cause:** Invalid JWT proof signature or incorrect nonce.

**Solution:**
1. Verify the wallet is using the correct `c_nonce` from the token response
2. Check that the proof is signed with a valid P-256 EC key
3. Ensure the `aud` claim matches the credential issuer URL

### "Docker image not working"

**Cause:** Using Docker Hub images instead of locally built ones.

**Solution:** EUDI compatibility requires custom-built images:
```bash
./gradlew :waltid-services:waltid-issuer-api:jibDockerBuild
docker tag waltid/issuer-api:latest waltid/issuer-api:stable
docker compose --profile identity up -d --force-recreate issuer-api
```

## Testing with EUDI Reference Wallet

1. Install the EUDI Reference Wallet on Android/iOS
2. Start the issuer stack with custom images
3. Use the Web Portal to generate a credential offer QR code
4. Scan the QR code with the EUDI wallet
5. Complete the issuance flow in the wallet

## Key Verification Points

- Credential appears in wallet with correct data
- Credential type shows correctly (PID, mDL)
- Claims are properly displayed
- Credential can be presented to verifiers

## Related Documentation

- [Deployment Guide](./deployment-guide.md) - Operations and deployment
- [Credential Formats Reference](./credential-formats.md) - Quick reference for all formats
- [OpenID4VCI Specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [EUDI Wallet Architecture](https://github.com/eu-digital-identity-wallet/architecture-and-reference-framework)
