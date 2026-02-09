<div align="center">
<h1>walt.id IdP Kit</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Proof of Concept: OpenID Connect Identity Provider using Verifiable Credentials for authentication</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟡%20Unmaintained-yellow?style=for-the-badge&logo=warning" alt="Status: Unmaintained" />
    <br/>
    <em>This project is not actively maintained. Certain features may be outdated or not working as expected.<br />We encourage users to contribute to the project to help keep it up to date.</em>
  </p>

</div>

## What This Library Contains

`waltid-idpkit` is a **Proof of Concept (POC)** implementation of an OpenID Connect (OIDC) Identity Provider that uses Verifiable Credentials for user authentication instead of traditional username/password authentication. It demonstrates how to bridge the gap between credential-based authentication and OIDC-compatible systems.

## Main Purpose

This library was created as a POC to demonstrate:

- **Credential-Based Authentication**: Using Verifiable Credentials as the authentication mechanism for OIDC flows
- **OIDC Token Issuance**: Issuing standard OIDC ID tokens and access tokens after credential verification
- **Claim Mapping**: Extracting claims from Verifiable Credentials using JSONPath and mapping them to OIDC claims
- **Verifier Integration**: Integrating with walt.id verifier services to validate credential presentations

## Key Concepts

### OpenID Connect with Verifiable Credentials

Traditional OIDC flows use username/password or other authentication methods. This POC replaces that with Verifiable Credentials:

1. **Authorization Request**: Relying Party (RP) redirects user to IdP's `/authorize` endpoint
2. **Credential Request**: IdP requests credential presentation via verifier service
3. **Credential Presentation**: User presents Verifiable Credential from their wallet
4. **Credential Verification**: Verifier validates the credential
5. **Claim Extraction**: IdP extracts claims from verified credential using JSONPath mapping
6. **Token Issuance**: IdP issues OIDC ID token and access token
7. **User Info**: RP can access user information via `/userinfo` endpoint

### Claim Mapping

The library uses JSONPath expressions to extract specific claims from Verifiable Credentials:

```json
{
  "claimMapping": {
    "id": "$.credentialSubject.id",
    "familyName": "$.credentialSubject.familyName",
    "firstName": "$.credentialSubject.firstName",
    "dateOfBirth": "$.credentialSubject.dateOfBirth"
  }
}
```

These mapped claims are then included in the OIDC ID token and available via the userinfo endpoint.

### OIDC Endpoints

The library implements standard OIDC endpoints:

- **`.well-known/openid-configuration`**: OpenID Provider metadata
- **`/authorize`**: Authorization endpoint (initiates credential presentation flow)
- **`/token`**: Token endpoint (exchanges authorization code for tokens)
- **`/userinfo`**: UserInfo endpoint (returns user claims)
- **`/jwks`**: JSON Web Key Set (public keys for token verification)

## Assumptions and Dependencies

### Dependencies

- **Ktor Server**: HTTP server framework
- **Ktor Client**: HTTP client for verifier communication
- **Nimbus JOSE JWT**: JWT creation and signing
- **Google Tink**: Cryptographic operations (Ed25519 support)
- **JSONPath**: JSONPath expressions for claim extraction
- **Kotlinx Serialization**: JSON serialization

### Configuration

The library requires an `idp-config.json` file with:

- **key**: JWK for signing tokens (Ed25519)
- **verifierRequest**: Credential request configuration for verifier
- **verifierUrl**: URL of the verifier service
- **issuer**: Issuer identifier (IdP URL)
- **redirectUrl**: Redirect URL after credential presentation
- **walletUrl**: Wallet URL for credential presentation
- **claimMapping**: JSONPath mappings from credential to OIDC claims
- **enableDebug**: Enable debug mode (auto-login)

## Usage

### Configuration

Create an `idp-config.json` file:

```json
{
  "key": "{\"kty\":\"OKP\",\"d\":\"...\",\"crv\":\"Ed25519\",\"x\":\"...\"}",
  "verifierRequest": {
    "request_credentials": [
      {
        "format": "jwt_vc_json",
        "type": "OpenBadgeCredential"
      }
    ]
  },
  "issuer": "http://localhost:8080",
  "redirectUrl": "http://localhost:8080/login",
  "walletUrl": "http://localhost:7101/api/siop/initiatePresentation",
  "enableDebug": true,
  "verifierUrl": "http://localhost:7003",
  "claimMapping": {
    "id": "$.credentialSubject.id",
    "familyName": "$.credentialSubject.familyName",
    "firstName": "$.credentialSubject.firstName"
  }
}
```

### Running the Application

```bash
# Build the application
./gradlew :waltid-libraries:auth:waltid-idpkit:build

# Run the application
./gradlew :waltid-libraries:auth:waltid-idpkit:run
```

The server will start on port 8080 (configurable).

### OIDC Flow Example

1. **RP initiates authorization**:
   ```
   GET /authorize?response_type=code&client_id=my-client&redirect_uri=http://rp.example.com/callback&scope=openid%20profile
   ```

2. **IdP requests credential presentation**:
   - Creates verification request via verifier service
   - Returns QR code or URL for credential presentation

3. **User presents credential**:
   - User scans QR code or clicks link
   - Wallet presents credential to verifier

4. **IdP verifies and issues tokens**:
   - Polls verifier for verification result
   - Extracts claims using JSONPath mapping
   - Issues OIDC ID token and access token

5. **RP exchanges code for tokens**:
   ```
   POST /token
   grant_type=authorization_code&code=...&redirect_uri=...
   ```

6. **RP accesses user info**:
   ```
   GET /userinfo
   Authorization: Bearer <access_token>
   ```

### Key Components

- **App.kt**: Main Ktor application with OIDC endpoints
- **Verifier.kt**: Integration with walt.id verifier service
- **IdToken.kt**: OIDC ID token structure
- **PocIdpKitConfiguration.kt**: Configuration data class

## Limitations and Notes

⚠️ **This is a POC and has not been maintained or developed since creation.**

- **Not Production Ready**: This is a proof of concept, not production code
- **Limited Error Handling**: Error handling is minimal
- **In-Memory Storage**: Uses in-memory caches (not persistent)
- **Hardcoded Values**: Some values are hardcoded (e.g., client IDs)
- **No Client Registration**: No dynamic client registration
- **Basic Security**: Security features are minimal

## Related Libraries

- **[waltid-verifier-api](../waltid-services/waltid-verifier-api)**: Verifier service for credential validation
- **[waltid-ktor-authnz](../waltid-libraries/auth/waltid-ktor-authnz)**: Production-ready authentication framework
- **[waltid-openid4vc](../waltid-libraries/protocols/waltid-openid4vc)**: OpenID4VC protocol implementation

## Use Cases

This POC demonstrates:

- **Credential-Based SSO**: Using Verifiable Credentials for Single Sign-On
- **OIDC Bridge**: Bridging credential-based authentication to OIDC-compatible systems
- **Claim Extraction**: Extracting structured data from credentials for OIDC claims
- **Integration Pattern**: Pattern for integrating credential verification with OIDC flows

## Future Development

This library is currently unmaintained. For production use, consider:

- Using **[waltid-ktor-authnz](../waltid-libraries/auth/waltid-ktor-authnz)** for authentication
- Implementing proper client registration
- Adding persistent storage
- Enhancing security features
- Adding comprehensive error handling

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>

