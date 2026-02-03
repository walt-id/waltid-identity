# EUDI Credential Formats Reference

Quick reference for credential formats supported by the EUDI wallet.

## Format Comparison

| Property | mDoc (mso_mdoc) | SD-JWT (dc+sd-jwt) |
|----------|-----------------|-------------------|
| Encoding | CBOR | JSON/JWT |
| Selective Disclosure | Built-in | Disclosure array |
| Standard | ISO 18013-5 | IETF SD-JWT |
| Binding Method | `cose_key` | `jwk` |
| Type Identifier | `doctype` | `vct` |

## EUDI PID (mDoc)

```hocon
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
    name = "EU Personal ID"
    locale = "en"
    logo = { url = "https://example.com/pid-logo.png" }
    background_color = "#12107c"
    text_color = "#FFFFFF"
  }]
  claims = {
    "eu.europa.ec.eudi.pid.1" = {
      family_name = { mandatory = true, display = [{ name = "Family Name" }] }
      given_name = { mandatory = true, display = [{ name = "Given Name" }] }
      birth_date = { mandatory = true, display = [{ name = "Date of Birth" }] }
      issuing_country = { mandatory = true }
      issuing_authority = { mandatory = true }
    }
  }
}
```

### Issuance Request

```json
{
  "issuerKey": { "type": "jwk", "jwk": { ... } },
  "issuerDid": "did:key:...",
  "credentialConfigurationId": "eu.europa.ec.eudi.pid.1",
  "credentialData": {
    "family_name": "DOE",
    "given_name": "JOHN",
    "birth_date": "1990-01-15",
    "issuing_country": "DE",
    "issuing_authority": "German Government",
    "document_number": "123456789"
  }
}
```

## EUDI PID (SD-JWT)

```hocon
"eu.europa.ec.eudi.pid_vc_sd_jwt" {
  format = "dc+sd-jwt"
  vct = "urn:eudi:pid:1"
  cryptographic_binding_methods_supported = ["jwk"]
  proof_types_supported = {
    jwt = {
      proof_signing_alg_values_supported = ["ES256"]
    }
  }
  display = [{
    name = "EU Personal ID (SD-JWT)"
    locale = "en"
  }]
  claims = {
    family_name = { mandatory = true, display = [{ name = "Family Name" }] }
    given_name = { mandatory = true, display = [{ name = "Given Name" }] }
    birth_date = { mandatory = true, display = [{ name = "Date of Birth" }] }
  }
}
```

### Issuance Request

```json
{
  "issuerKey": { "type": "jwk", "jwk": { ... } },
  "issuerDid": "did:key:...",
  "credentialConfigurationId": "eu.europa.ec.eudi.pid_vc_sd_jwt",
  "credentialData": {
    "family_name": "DOE",
    "given_name": "JOHN",
    "birth_date": "1990-01-15"
  },
  "selectiveDisclosure": {
    "fields": {
      "family_name": { "sd": true },
      "given_name": { "sd": true },
      "birth_date": { "sd": true }
    }
  }
}
```

## Mobile Driving License (mDL)

```hocon
"org.iso.18013.5.1.mDL" {
  format = "mso_mdoc"
  doctype = "org.iso.18013.5.1.mDL"
  cryptographic_binding_methods_supported = ["cose_key"]
  proof_types_supported = {
    jwt = {
      proof_signing_alg_values_supported = ["ES256"]
    }
  }
  display = [{
    name = "Mobile Driving License"
    locale = "en"
    logo = { url = "https://example.com/mdl-logo.png" }
    background_color = "#ffffff"
    text_color = "#000000"
  }]
  claims = {
    "org.iso.18013.5.1" = {
      family_name = { mandatory = true }
      given_name = { mandatory = true }
      birth_date = { mandatory = true }
      issue_date = { mandatory = true }
      expiry_date = { mandatory = true }
      issuing_country = { mandatory = true }
      issuing_authority = { mandatory = true }
      document_number = { mandatory = true }
      driving_privileges = { mandatory = true }
    }
  }
}
```

### Issuance Request

```json
{
  "issuerKey": { "type": "jwk", "jwk": { ... } },
  "issuerDid": "did:key:...",
  "credentialConfigurationId": "org.iso.18013.5.1.mDL",
  "credentialData": {
    "family_name": "DOE",
    "given_name": "JOHN",
    "birth_date": "1990-01-15",
    "issue_date": "2023-01-01",
    "expiry_date": "2033-01-01",
    "issuing_country": "DE",
    "issuing_authority": "Vehicle Registration Authority",
    "document_number": "DL123456",
    "driving_privileges": [
      {
        "vehicle_category_code": "B",
        "issue_date": "2023-01-01",
        "expiry_date": "2033-01-01"
      }
    ]
  }
}
```

## Format Strings

### Correct Usage

| Credential Type | Format String | Notes |
|-----------------|---------------|-------|
| PID (mDoc) | `mso_mdoc` | ISO 18013-5 |
| PID (SD-JWT) | `dc+sd-jwt` | EUDI requires this |
| mDL | `mso_mdoc` | ISO 18013-5 |

### Common Mistakes

| Wrong | Correct | Issue |
|-------|---------|-------|
| `vc+sd-jwt` | `dc+sd-jwt` | EUDI uses Digital Credentials format |
| `mdoc` | `mso_mdoc` | Must include `mso_` prefix |
| `sdjwt` | `dc+sd-jwt` | Full MIME type required |

## Proof Types

### JWT Proof (Required)

All EUDI credentials require JWT proofs:

```json
{
  "proofs": {
    "jwt": ["eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0IiwiandrlI..."]
  }
}
```

### CWT Proof (Not Supported)

EUDI wallets currently only support JWT proofs. CWT proofs are not used.

## Key Types

### P-256 (Required)

EUDI wallets use P-256 (secp256r1) keys:

```json
{
  "kty": "EC",
  "crv": "P-256",
  "x": "base64url-encoded-x-coordinate",
  "y": "base64url-encoded-y-coordinate"
}
```

### Supported Algorithms

| Algorithm | Curve | Usage |
|-----------|-------|-------|
| ES256 | P-256 | Primary |
| ES384 | P-384 | Supported |
| ES512 | P-521 | Supported |

## VCT Values

| Credential | VCT |
|------------|-----|
| EUDI PID | `urn:eudi:pid:1` |

## DocType Values

| Credential | DocType |
|------------|---------|
| EUDI PID | `eu.europa.ec.eudi.pid.1` |
| mDL | `org.iso.18013.5.1.mDL` |

## API Endpoints

| Format | Endpoint | Example |
|--------|----------|---------|
| mDoc | `POST /openid4vc/mdoc/issue` | PID, mDL |
| SD-JWT | `POST /openid4vc/sdjwt/issue` | PID SD-JWT |
| JWT | `POST /openid4vc/jwt/issue` | Generic JWT-VC |

## Response Format

Credential offer URI:
```
openid-credential-offer://?credential_offer_uri=https://issuer.example.com/openid4vc/draft13/credentialOffer?id=abc123
```

## Quick Copy-Paste

### Minimal PID mDoc Configuration

```hocon
"eu.europa.ec.eudi.pid.1" {
  format = "mso_mdoc"
  doctype = "eu.europa.ec.eudi.pid.1"
  cryptographic_binding_methods_supported = ["cose_key"]
  proof_types_supported = { jwt = { proof_signing_alg_values_supported = ["ES256"] } }
}
```

### Minimal PID SD-JWT Configuration

```hocon
"eu.europa.ec.eudi.pid_vc_sd_jwt" {
  format = "dc+sd-jwt"
  vct = "urn:eudi:pid:1"
  cryptographic_binding_methods_supported = ["jwk"]
  proof_types_supported = { jwt = { proof_signing_alg_values_supported = ["ES256"] } }
}
```

### Minimal mDL Configuration

```hocon
"org.iso.18013.5.1.mDL" {
  format = "mso_mdoc"
  doctype = "org.iso.18013.5.1.mDL"
  cryptographic_binding_methods_supported = ["cose_key"]
  proof_types_supported = { jwt = { proof_signing_alg_values_supported = ["ES256"] } }
}
```
