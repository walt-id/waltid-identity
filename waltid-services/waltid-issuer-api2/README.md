# walt.id Issuer API 2

by [walt.id](https://walt.id)

Ktor-based Issuer REST service implementing OpenID4VCI 1.0 with stateless architecture and config-based profiles



## Status

  
*This project is being actively maintained by the development team at walt.id.*  
*Regular updates, bug fixes, and new features are being added.*

## What is Issuer API 2?

A reference implementation of Issuer HTTP service for issuing verifiable credentials to wallets using **OpenID for Verifiable Credential Issuance (OpenID4VCI) 1.0**. It exposes REST endpoints to create credential offers, manage issuance sessions, and issue credentials in multiple formats.

Refer to the
walt.id documentation (TBD, use the [enterprise docs](https://docs.walt.id/enterprise-stack/services/issuer2-service/overview) in the meantime)
for a detailed view on using the issuer service, or learn more about OpenID4VCI [here](https://docs.walt.id/concepts/data-exchange-protocols/openid4vci).

## Supported credential formats

- `dc+sd-jwt` — IETF SD-JWT VCs (with selective disclosure)
- `jwt_vc_json` — W3C Verifiable Credentials as JWT
- `mso_mdoc` — ISO mdoc (e.g., mobile Driving License)

Backed by `waltid-digital-credentials` and `waltid-mdoc-credentials2` for credential creation and signing.

## Key features

- Create and manage issuance sessions (pre-authorized and authorization code flows)
- Config-based credential profiles for easy deployment
- Support for external OAuth providers (e.g., Keycloak) for authorization code flow
- ID token claims mapping to credential data
- SSE-based live session updates + optional webhooks
- OpenAPI documentation with comprehensive examples
- mDOC namespace data mapping for ISO credentials

## Authentication Methods

### Pre-Authorized Code Flow

Direct credential issuance without user authentication. The issuer generates a pre-authorized code that the wallet exchanges for credentials.

- Support for txCode (generated or given)

### Authorization Code Flow

User authentication via external OAuth provider (e.g., Keycloak). Supports:

- External OAuth provider redirection
- ID token claims mapping to credential data
- PKCE support

## Endpoints

### Metadata Endpoints

- `GET /.well-known/openid-credential-issuer` — Credential issuer metadata
- `GET /.well-known/oauth-authorization-server` — OAuth authorization server metadata

### OpenID4VCI Protocol Endpoints

- `GET /authorize` — Authorization endpoint (redirects to external OAuth provider)
- `GET /callback` — OAuth callback handler for external provider
- `POST /token` — Token endpoint for code exchange
- `POST /nonce` — Nonce endpoint for credential binding
- `POST /credential` — Credential endpoint for credential issuance

### Issuance Management Endpoints

- `GET /profiles` — List available credential profiles
- `GET /profiles/{profileId}` — Get profile details
- `POST /issuance/create-offer` — Create a credential offer
- `GET /credential-offer` — Retrieve credential offer by session ID
- `GET /sessions/{sessionId}` — Get session status
- `GET /sessions/{sessionId}/events` — SSE stream for session updates

## Quick start

Run locally (development):

```bash
./gradlew :waltid-services:waltid-issuer-api2:run
```

Docker images:

```bash
# Development (local Docker daemon, single-arch)
./gradlew :waltid-services:waltid-issuer-api2:jibDockerBuild
# image: waltid/issuer-api2:<version>
```

```bash
# Production (multi-arch push to your registry)
export DOCKER_USERNAME=<your-dockerhub-namespace>
export DOCKER_PASSWORD=<your-dockerhub-token>
./gradlew :waltid-services:waltid-issuer-api2:jib
# image: docker.io/<DOCKER_USERNAME>/issuer-api2:<version>
```

Note: multi-arch images require a registry push. Local tar output is single-arch only.

### List available profiles

```http
GET /profiles
```

### Create a credential offer

```http
POST /issuance/create-offer
Content-Type: application/json

{
  "profileId": "identity-credential-sdjwt",
  "authMethod": "PRE_AUTHORIZED"
}
```

Response:

```json
{
  "sessionId": "abc123",
  "credentialOffer": "openid-credential-offer://?credential_offer=...",
  "expiresAt": "2025-04-18T12:00:00Z"
}
```

### Subscribe to session updates

```http
GET /sessions/{sessionId}/events
Accept: text/event-stream
```

## Configuration

### Service Configuration (`issuer-service.conf`)

```hocon
baseUrl = "https://issuer.example.com"

tokenKey = {
    type: "jwk"
    jwk: { ... }
}

# Optional: Default webhook notifications
defaultNotifications = {
    webhook = {
        url = "https://webhook.example.com/callback"
    }
}

# Optional: External OAuth provider for authorization code flow
authProviderConfiguration = {
    name = "Keycloak"
    authorizeUrl = "https://keycloak.example.com/auth"
    accessTokenUrl = "https://keycloak.example.com/token"
    clientId = "issuer_api"
    clientSecret = "secret"
    defaultScopes = ["openid", "profile"]
}
```

### Credential Profiles (`profiles.conf`)

```hocon
credentialConfigurations = {
    identity_credential = {
        format = "dc+sd-jwt"
        vct = "https://example.com/identity_credential"
        # ... credential configuration
    }
}

profiles = [
    {
        profileId = "identity-credential-sdjwt"
        credentialConfigurationId = "identity_credential"
        issuerKey = { ... }
        credentialData = { ... }
        selectiveDisclosure = { ... }
    }
]
```

See `config/` for complete configuration examples including web server settings and feature toggles.

## Related projects

- `waltid-openid4vci` — OpenID4VCI 1.0 core library
- `waltid-digital-credentials` — Unified credential handling
- `waltid-mdoc-credentials2` — mDOC/mDL credential support
- `waltid-sdjwt` — SD-JWT implementation
- `waltid-ktor-notifications` — SSE and webhook notifications

## Join the community

- Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
- Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

