# Identity Credential

This is an example of an IETF SD-JWT (Selective Disclosure JWT) Verifiable Credential for identity verification.

## Example SD-JWT

```json
{
  "header": {
    "alg": "ES256",
    "typ": "JWT"
  },
  "payload": {
    "iss": "https://issuer.example.com",
    "iat": 1640995200,
    "exp": 1672531200,
    "vct": "https://www.w3.org/2018/credentials/v1",
    "sub": "did:example:123456789abcdef",
    "cnf": {
      "jwk": {
        "kty": "EC",
        "crv": "P-256",
        "x": "base64url-encoded-x-coordinate",
        "y": "base64url-encoded-y-coordinate"
      }
    },
    "sd_hash": "sha256-hash-of-disclosed-claims"
  }
}
```

## Selective Disclosure Claims

The following claims can be selectively disclosed:

- **Name**: `$.givenName`
- **Surname**: `$.familyName` 
- **Date of Birth**: `$.birthDate`
- **Nationality**: `$.nationality`
- **Document Type**: `$.documentType`
- **Document Number**: `$.documentNumber`
- **Issuing Authority**: `$.issuingAuthority`
- **Valid From**: `$.validFrom`
- **Valid Until**: `$.validUntil`

## Usage

This credential demonstrates the IETF SD-JWT standard for selective disclosure of verifiable credential claims, allowing users to prove specific attributes without revealing their entire identity.
