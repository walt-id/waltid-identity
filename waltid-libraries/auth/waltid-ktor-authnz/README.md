<div align="center">
<h1>walt.id Ktor AuthNZ</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Flexible authentication and authorization framework for Ktor applications</p>

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

## What This Library Contains

`waltid-ktor-authnz` is an authentication framework for Ktor: logins as flows of one or more methods (password, TOTP,
passkeys, OIDC, verifiable credentials, ...), sessions and login tokens, account enrolment (TOTP, recovery codes,
passkeys, passwords) and protections (attempt limits, single-use challenges, revocation). Authorization is handled by
`waltid-permissions`.

## Key Concepts

- **Account store** (`EditableAccountStore`, provided by the application): maps account identifiers (username, email,
  OIDC issuer + subject, passkey credential id, ...) to accounts, and keeps each method's stored data (password
  hashes, TOTP secrets, passkeys). `InMemoryAccountStore` is a complete one to start with.
- **Authentication method** (`AuthenticationMethod`): one way to prove identity; each serves routes named by its id.
  Built in: `userpass`, `email`, `email-code`, `totp`, `totp-setup`, `recovery-code`, `passkey`, `ldap`, `radius`, `jwt`, `oidc`, `vc`,
  `web3`, and `identify`, which finds the account first and offers the ways it can log in.
- **Auth flow** (`AuthFlow`): a tree of methods, e.g. a password, then TOTP or a recovery code:

  ```json
  {"method": "email", "expiration": "7d", "continue": [
    {"method": "totp", "success": true},
    {"method": "recovery-code", "success": true}
  ]}
  ```

- **Session** (`AuthSession`): the progress of one login through its flow. Responses (`AuthSessionInformation`) name
  the `next_method` - which is also the next URL - and, once complete, the login `token`.
- **Token handler**: opaque tokens (validated against their live session: logout, expiry and revocation end them) or
  JWTs (stateless: valid until `exp`, unless `requireActiveSession` is set). Optional refresh tokens.

## Usage

### Setup

```kotlin
install(KtorAuthnz) {
    accountStore = MyAccountStore                    // required
    sessionStore = ValkeySessionStore(null, "localhost", 6379, null, null)   // default: in memory
    expiringStore = ValkeyExpiringStore(null, "localhost", 6379, null, null) // default: in memory
    tokenHandler = JwtTokenHandler.crypto2(signingKey, algorithm = JwsAlgorithm.ES256) // default: opaque tokens
    refreshTokens = RefreshTokenSettings(accessTokenLifetime = 15.minutes, refreshTokenLifetime = 30.days) // optional
    passkeys = PasskeySettings(rpId = "example.com", rpName = "Example", origins = setOf("https://app.example.com"))
    emailCodes = EmailCodeSettings { delivery -> mailer.sendCode(delivery) }  // for the email-code method
    cookie { domain = ".example.com" }
    onEvent { event -> auditLog.write(event) }
}

install(Authentication) {
    ktorAuthnz("ktor-authnz") {
        validate { principal -> principal.takeIf { isActive(it.accountId) } } // optional: check or enrich the caller
    }
}
```

The configuration is process-wide (kept in `KtorAuthnzManager`): one JVM runs one ktor-authnz configuration. Use
Valkey stores when a service runs on more than one instance.

### Login routes

```kotlin
routing {
    route("auth") {
        authFlows(flows) { tenant = { request.host() } }
    }
}
```

`authFlows` serves every step of the flows:

| Route | Purpose |
|---|---|
| `POST auth/{method}` | first step of the flow starting with that method (session opened implicitly) |
| `POST auth/{sessionId}/{method}` | a later step, e.g. `auth/{sessionId}/totp` |
| `POST auth/start?flow={method}` | open a session explicitly (optional, `explicitStart`) |

For flows chosen per call (per tenant), pass the methods and a resolver:
`authFlows(methods = ..., flowsFor = { flowsOfTenant(request.host()) })`.

A two-step login:

```http
POST /auth/email            {"email": "alice@example.com", "password": "..."}
-> {"session_id": "caf4...", "status": "CONTINUE_NEXT_FLOW", "next_method": ["totp", "recovery-code"]}

POST /auth/caf4.../totp     {"code": "768944"}
-> {"session_id": "caf4...", "status": "SUCCESS", "token": "..."}
```

### Protected routes

```kotlin
authenticate("ktor-authnz") {
    get("/me") {
        val principal = call.authnzPrincipal()!!   // token, accountId, sessionId
        call.respond(principal.accountId)
    }
}
```

The token is read from the `ktor-authnz-auth` header, a Bearer `Authorization` header, or the session cookie.

### Enrolment and account routes

Place inside `authenticate { }`:

- `totp-setup` in a flow, next to `totp`, sets TOTP up during login for accounts that have none yet (an account
  that has TOTP is refused there): `{"method": "email", "continue": [{"method": "totp", "success": true},
  {"method": "totp-setup", "success": true, "config": {"issuer": "Example"}}]}`
- `totpEnrollment(issuer = "Example")`: `totp/enroll`, `totp/enroll/confirm` (answers recovery codes), `DELETE totp`,
  `recovery-codes`
- `passkeyEnrollment { accountId -> emailOf(accountId) }`: `passkey/register/options`, `passkey/register`,
  `DELETE passkey/{credentialId}`
- `passwordChange(EmailPass)`: `password/change` (ends every session of the account)

Public:

- `passwordReset(EmailPass) { email, token -> mailer.sendResetLink(email, token) }`: `password/reset/request`,
  `password/reset/confirm`
- `tokenRefresh()`: `token/refresh` (single-use refresh tokens; a reused one ends the session)

### Protections

- **Attempt limits** (`AttemptLimits`): a session fails after 5 failed steps; an identifier is locked for 15 minutes
  after 10 failures (429). Configurable, `AttemptLimits.DISABLED` turns them off.
- **Single use**: TOTP codes, recovery codes, passkey and Web3 challenges, reset and refresh tokens.
- **Sessions**: a failed first step stores nothing; unfinished sessions expire after 15 minutes; sessions are bound
  to their tenant (see [multi-tenant.md](docs/multi-tenant.md)).
- **Events** (`AuthnzEvent`): login steps succeeded/failed, attempts exceeded, logout, sessions revoked, methods
  enrolled/removed, password changed/reset, token refreshed.

### Verifiable credential login

The `vc` method verifies a presented credential with a verifier2 service and logs in the account of one of its
claims:

```json
{"method": "vc", "success": true, "config": {
  "verifierUrl": "https://verifier.example.com",
  "setup": {"flow_type": "cross_device", "core_flow": {"dcql_query": {"credentials": [
    {"id": "pid", "format": "dc+sd-jwt", "meta": {"vct_values": ["urn:eudi:pid:1"]}}]}}},
  "identifierClaim": ["personal_administrative_number"]
}}
```

`POST auth/vc/start` answers the authorization request URL (QR code or same-device link); poll
`GET auth/{sessionId}/vc/status` until the login completes. Unknown accounts are registered through the
`Registration` function amendment, if given.

### Custom methods

Extend `AuthenticationMethod`, serve routes named by its id, call `getAuthSession(authContext)` and then
`handleAuthSuccess(session, authContext(call), accountId)`; throw an `AuthException` (e.g. `authFailure("...")`) on a
failed check, so that it counts as a failed attempt. Register it with `AuthMethodManager.registerAuthenticationMethod`.
See [new-auth-method.md](docs/new-auth-method.md).

## Examples and Tests

[`examples/multitenant`](src/test/kotlin/id/walt/ktorauthnz/examples/multitenant/MultiTenantApp.kt): an application
whose tenants each configure their own login (password + TOTP + email code, OIDC, LDAP + TOTP, verifiable credential),
run by `MultiTenantAppTest` against a mock IdP, a mock verifier and an in-memory LDAP server.

[`examples/identifierfirst`](src/test/kotlin/id/walt/ktorauthnz/examples/identifierfirst/IdentifierFirstApp.kt): the
user enters an address and is offered what the account set up (password + TOTP or email code, passkey or wallet, IdP
or LDAP + TOTP, an IdP for a whole domain), run by `IdentifierFirstAppTest`. Both examples are compiled with the
tests, so they follow the API.

The tests double as examples: `AuthFlowRoutesTest` (flows, tenants, provider hook), `TotpEnrollmentTest`,
`PasswordRoutesTest`, `PasskeyTest`, `RefreshTokensTest`, `OidcLoginTest` (mock IdP), `VerifiableCredentialLoginTest`
(mock verifier2), `ExampleWeb` (an example app). `ValkeyStoresTest` runs against a Valkey given by `VALKEY_TEST_PORT`.

## Further Documentation

- [1.Quickstart.md](docs/1.Quickstart.md): flows and requests, step by step
- [multi-tenant.md](docs/multi-tenant.md): a login configured per tenant
- [identifier-first.md](docs/identifier-first.md): the account decides how it logs in
- [new-auth-method.md](docs/new-auth-method.md): adding an authentication method
- [oidc.md](docs/oidc.md): OpenID Connect
- [radius.md](docs/radius.md): RADIUS

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>

