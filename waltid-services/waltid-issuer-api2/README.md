<div align="center">
 <h1>Issuer API 2</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>OpenID4VCI 1.0 credential issuance service</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

## Overview

Issuer API 2 is the modern walt.id issuer service for OpenID for Verifiable Credential Issuance (OpenID4VCI) 1.0. It provides profile-based credential offers, OpenID4VCI metadata, authorization and token endpoints, credential issuance, nonce handling, session inspection, and Server-Sent Events for issuance updates.

Use this service for new issuer integrations that need OpenID4VCI 1.0 support. The original `waltid-issuer-api` remains available for draft-based and legacy flows, but is planned for deprecation.

## Features

- **OpenID4VCI 1.0** — Credential offer, authorization, token, nonce, and credential endpoints
- **Credential profiles** — Configurable issuance profiles in `issuer2-profiles.conf`
- **Metadata endpoints** — Credential issuer, authorization server, JWT VC issuer, JWKS, and VCT metadata
- **Grant types** — Pre-authorized code and authorization code flows
- **Session tracking** — Inspect issuance sessions and stream updates via SSE
- **Pluggable persistence** — In-memory by default, Redis-backed repositories when enabled
- **KMS support** — Uses the shared crypto stack, including software keys and cloud-backed integrations

## Running

From the `waltid-identity` root:

```bash
./gradlew :waltid-services:waltid-issuer-api2:run
```

By default the service listens on `0.0.0.0:7002`.

## Configuration

Configuration files live in `config/`:

| File | Purpose |
|------|---------|
| `_features.conf` | Enables optional service features |
| `web.conf` | Host and port configuration |
| `issuer-service.conf` | Base URL and issuer token signing key |
| `credential-issuer-metadata.conf` | OpenID4VCI issuer and authorization server metadata |
| `issuer2-profiles.conf` | Credential profiles exposed by the management API |
| `persistence.conf` | In-memory or Redis-backed repository configuration |
| `authentication-service.conf` | Optional external OAuth authentication configuration |

The default `issuer-service.conf` uses `http://localhost:7002` as `baseUrl`. Update this value when deploying behind a public host or reverse proxy so generated metadata and credential offers contain externally reachable URLs.

## API Endpoints

### Management API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/issuer2/profiles` | List configured credential profiles |
| `GET` | `/issuer2/profiles/{profileId}` | Get a credential profile |
| `POST` | `/issuer2/credential-offers` | Create a credential offer |
| `GET` | `/issuer2/sessions` | List issuance sessions |
| `GET` | `/issuer2/sessions/{sessionId}` | Get an issuance session |
| `GET` | `/issuer2/sessions/{sessionId}/events` | Stream issuance session updates via SSE |

### OpenID4VCI API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/.well-known/openid-credential-issuer/openid4vci` | Credential issuer metadata |
| `GET` | `/.well-known/oauth-authorization-server/openid4vci` | Authorization server metadata |
| `GET` | `/.well-known/jwt-vc-issuer/openid4vci` | JWT VC issuer metadata |
| `GET` | `/.well-known/vct/{type}` | SD-JWT VC type metadata |
| `GET` | `/openid4vci/jwks` | Issuer signing keys |
| `GET` | `/openid4vci/credential-offer?id={sessionId}` | Credential offer by reference |
| `GET` | `/openid4vci/authorize` | Authorization endpoint |
| `POST` | `/openid4vci/token` | Token endpoint |
| `POST` | `/openid4vci/nonce` | Nonce endpoint |
| `POST` | `/openid4vci/credential` | Credential endpoint |

## Creating a Credential Offer

Create offers by selecting a configured profile:

```bash
curl -X POST http://localhost:7002/issuer2/credential-offers \
  -H "Content-Type: application/json" \
  -d '{
    "profileId": "UniversityDegree",
    "credentialData": {
      "credentialSubject": {
        "givenName": "Jane",
        "familyName": "Doe"
      }
    }
  }'
```

The response contains the credential offer URL or offer reference that a wallet can use to start the OpenID4VCI flow.

## Persistence

The default persistence configuration is in-memory:

```hocon
type = "memory"
```

Redis-backed repositories can be enabled in `persistence.conf`:

```hocon
type = "redis"
nodes = [{ host = "127.0.0.1", port = 6379 }]
```

Redis repository tests are excluded from the default test task. Run them explicitly with:

```bash
ISSUER2_REDIS_HOST=127.0.0.1 ./gradlew :waltid-services:waltid-issuer-api2:redisTest
```

## Building and Testing

```bash
./gradlew :waltid-services:waltid-issuer-api2:build
./gradlew :waltid-services:waltid-issuer-api2:test
```

Build a local Docker image:

```bash
./gradlew :waltid-services:waltid-issuer-api2:jibDockerBuild
```

## Related Libraries

- **[waltid-openid4vci](../../waltid-libraries/protocols/waltid-openid4vci)** — OpenID4VCI 1.0 OAuth2 provider library
- **[waltid-crypto](../../waltid-libraries/crypto/waltid-crypto)** — Key management and signing
- **[waltid-did](../../waltid-libraries/waltid-did)** — DID creation and resolution
- **[waltid-sdjwt](../../waltid-libraries/sdjwt/waltid-sdjwt)** — SD-JWT credential support
- **[waltid-mdoc-credentials](../../waltid-libraries/credentials/waltid-mdoc-credentials)** — mdoc credential support

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
