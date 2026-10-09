# Routes for logged-in users

All of these go inside `authenticate { }`.

## Logout and sessions

```kotlin
authenticate("login") { accountRoutes() }
```

| route | does |
|---|---|
| `POST logout` | ends this session and its token, deletes the session cookie |
| `GET sessions` | the account's sessions: id, `current`, when it logged in and with which methods, expiry |
| `DELETE sessions/{sessionId}` | ends one of them; another account's session answers 404 |
| `DELETE sessions` | ends all of them, this one included |

Listing needs a session store that can list an account's sessions (`InMemorySessionStore`, `ValkeySessionStore`; others
answer 501). JWT login tokens are stateless: ending their session ends them only with `requireActiveSession` set on
`JwtTokenHandler`, otherwise they stay valid until they expire.

## When and how the user logged in

`KtorAuthnzPrincipal.authenticatedAt` and `.methods` (e.g. `["email", "totp"]`); JWT login tokens carry them as
`auth_time` and `amr`. For sensitive actions, ask for a fresh login:

```kotlin
post("email/change") {
    call.requireRecentLogin(5.minutes, anyOfMethods = setOf("totp", "passkey"))   // else 401
    ...
}
```

## Linking further identities

```kotlin
authenticate("login") {
    identityLinking(listOf(companyOidcFlow, directoryFlow), maxLoginAge = 10.minutes)
}
```

1. `POST link/oidc` answers a session.
2. The client continues it at the login routes as usual - `GET {sessionId}/oidc/auth`, the identity provider, the
   callback. The method must be served by `authFlows` (in its `methods`).
3. Once the method authenticated, the identity is the account's - nothing else is logged in. An identity that
   belongs to another account answers 409.

`GET identities` lists the account's identities, `DELETE identities?type=...&value=...` removes one (not the last one).
Both need `lookupAccountIdentifiers` in the account store (`InMemoryAccountStore` has it). Methods that link this way:
OIDC, LDAP, verifiable credentials, Web3. Passwords, TOTP and passkeys are set up with `passwordChange`,
`totpEnrollment` and `passkeyEnrollment`.
