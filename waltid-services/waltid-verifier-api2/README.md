<div align="center">
<h1>walt.id Verifier API 2</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Ktor-based Verifier REST service implementing OpenID4VP 1.0 with DCQL and modern policy engine</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
</div>

## What is Verifier API 2?

A production-ready Verifier HTTP service for requesting and validating verifiable presentations from wallets using **OpenID for Verifiable Presentations (OpenID4VP) 1.0**. It exposes REST endpoints to create verification sessions, provide authorization requests to wallets, receive `vp_token` responses, and stream session updates.

### How it differs from the original Verifier API

- **Protocol version**
  - Verifier API 2: Targets the final OpenID4VP 1.0 spec with **DCQL** (Digital Credentials Query Language).
  - Original Verifier API: Built around OpenID4VP draft flows, typically using **Presentation Definition** (PE/PEX) and draft semantics.

- **Policy engine**
  - Verifier API 2: Uses `waltid-verification-policies2` (modern, JSON-friendly, consistent `Result<JsonElement>` contract).
  - Original Verifier API: Uses `waltid-verification-policies` (legacy, aligned with earlier drafts and status flows).

- **Query format**
  - Verifier API 2: DCQL for credential requirements and combinations (`credential_sets`).
  - Original Verifier API: Presentation Definition (`input_descriptors`, `submission_requirements`).

- **Libraries used**
  - Verifier API 2: `waltid-openid4vp`, `waltid-openid4vp-verifier`, `waltid-dcql`, `waltid-digital-credentials`, `waltid-verification-policies2`, `waltid-vical`, `waltid-ktor-notifications`.
  - Original Verifier API: Draft-era OpenID4VP libs and the legacy policies library.

## Supported credential formats

- `dc+sd-jwt` — IETF SD-JWT VCs (with selective disclosure)
- `jwt_vc_json` — W3C Verifiable Credentials as JWT
- `mso_mdoc` — ISO mdoc (e.g., mobile Driving License)

Backed by `waltid-digital-credentials` for parsing and validation.

## Key features

- Create and manage verification sessions (same-device and cross-device)
- Generate Authorization Requests with DCQL requirements
- Receive and validate `vp_token` responses across formats
- Check overall DCQL fulfillment (including `credential_sets`)
- SSE-based live session updates + optional webhooks
- OpenAPI helpers for documentation and example payloads
- VICAL helper endpoints (fetch/validate) for mdoc trust lists

## Assumptions and dependencies

- Runtime: JVM (Ktor server, Java 21)
- Depends on:
  - `waltid-openid4vp`, `waltid-openid4vp-verifier`, `waltid-openid4vp-verifier-openapi`
  - `waltid-dcql`, `waltid-digital-credentials`, `waltid-verification-policies2`, `waltid-vical`
  - `waltid-ktor-notifications` (SSE and optional webhooks)

## Endpoints

Base path: `/verification-session`

- `POST /verification-session/create`
  - Body: VerificationSessionSetup (includes DCQL query, response mode, options)
  - Returns: Session creation response (authorization request URL(s) for wallet)

- `GET /verification-session/{sessionId}/info`
  - Returns: Current session object

- `GET /verification-session/{sessionId}/request`
  - Returns: Authorization Request (plain JSON or signed JAR JWT string)

- `POST /verification-session/{sessionId}/response`
  - Form fields: `vp_token` (required), `state` (recommended)
  - Action: Validates presentations, updates session status, returns a JSON result

- `GET /verification-session/{sessionId}/verification-session/events` (SSE)
  - Streams live session updates (`event`, `session`)

VICAL utilities:
- `POST /vical/fetch` — Fetch remote/file VICAL and return Base64
- `POST /vical/validate` — Validate VICAL with provided verification key

## Quick start

Run locally (development):

```bash
./gradlew :waltid-services:waltid-verifier-api2:run
```

Docker image:

```bash
./gradlew :waltid-services:waltid-verifier-api2:docker
# image: waltid/verifier-api2:<version>
```

Create a session (example payloads available via OpenAPI helper):

```http
POST /verification-session/create
Content-Type: application/json

{ "dcqlQuery": { "credentials": [ { "id": "id1", "format": "jwt_vc_json" } ] } }
```

Fetch Authorization Request (for wallet):

```http
GET /verification-session/{sessionId}/request
```

Receive response (direct_post mode):

```http
POST /verification-session/{sessionId}/response
Content-Type: application/x-www-form-urlencoded

vp_token={...}&state=xyz
```

Subscribe to updates:

```http
GET /verification-session/{sessionId}/verification-session/events
Accept: text/event-stream
```

## Configuration

See `config/` for service, web, and feature toggles. SSE and webhook delivery can be wired via `waltid-ktor-notifications`.

## Related projects

- `waltid-openid4vp` — OpenID4VP 1.0 core models
- `waltid-openid4vp-verifier` — Verifier session management and validation engine
- `waltid-dcql` — Digital Credentials Query Language
- `waltid-digital-credentials` — Unified credential parsing/validation
- `waltid-verification-policies2` — Modern verification policies

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
