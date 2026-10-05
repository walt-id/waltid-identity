# Multi-tenant applications

An application serving several tenants - organizations, customers - where each tenant configures its own login.
The complete, compiled and tested example is
[`examples/multitenant`](../src/test/kotlin/id/walt/ktorauthnz/examples/multitenant/MultiTenantApp.kt), with four
tenants:

| tenant | login |
|---|---|
| org1 | username + password, then TOTP, then a code sent by email |
| org2 | OIDC with the tenant's identity provider |
| org3 | LDAP against the tenant's directory, then TOTP |
| org4 | a verifiable credential presented from a wallet |

## 1. Scope routes to a tenant

```kotlin
routing {
    route("{tenant}") {
        authnzTenant { parameters["tenant"]!! }       // or request.host(), a header, ...

        route("auth") {
            authFlows(
                methods = listOf(UserPass, TOTP, EmailCode, OIDC, LDAP, VerifiableCredential),
                firstMethods = listOf(UserPass, OIDC, LDAP, VerifiableCredential),
                flowsFor = { tenants.flowsOf(authnzTenant!!) },
            )
        }
        authenticate("login") {
            get("me") { call.respond(call.authnzPrincipal()!!.tenant!!) }
        }
    }
}
```

Below `authnzTenant`:

- sessions are opened for the tenant and cannot be continued at another one (also not through an OIDC callback);
- account store calls see the tenant as `currentAuthnzTenant()`;
- attempt limits, password reset tokens and refresh tokens are kept per tenant;
- the provider accepts only tokens of logins to this tenant: a token of org1 answers 401 at org2. JWT login tokens
  carry the tenant as the `tenant` claim; `KtorAuthnzPrincipal.tenant` names it.

`authFlows` registers routes at startup - for every method in `methods`, and a starting route for each of
`firstMethods` - and asks `flowsFor` for the flows per call. A tenant whose flows do not start with a method answers
400 there.

## 2. Configure each tenant's flow

Flows are JSON (or `AuthFlow` objects), so they can live in your database. Method settings go in `config`:

```json
{"method": "ldap", "config": {"ldapServerUrl": "ldaps://ldap.org3.example", "userDNFormat": "uid=%s,ou=people,dc=org3"},
 "continue": [{"method": "totp", "success": true}]}
```

```json
{"method": "oidc", "success": true, "config": {
  "openIdConfigurationUrl": "https://idp.org2.example/.well-known/openid-configuration",
  "clientId": "...", "clientSecret": "...",
  "callbackUri": "https://app.example/org2/auth/oidc/callback"}}
```

## 3. Keep accounts per tenant

The account store interfaces take no tenant; a multi-tenant store reads `currentAuthnzTenant()` and filters by it:

```kotlin
override suspend fun lookupAccountUuid(identifier: AccountIdentifier) =
    db.accounts.findId(tenant = currentAuthnzTenant(), identifier = identifier.toDataString())
```

`InMemoryAccountStore` does this already - start with it, and set accounts up per tenant:

```kotlin
inAuthnzTenant("org1") {
    accounts.addAccount(
        UsernameIdentifier("alice"),
        identifierData = mapOf(UserPass.id to UserPassStoredData("password")),
        accountData = mapOf(TOTP.id to TOTPStoredData(totpSecret)),
    )
}
```

## 4. New users

OIDC logins create an account for a new identity. For LDAP users, wallet holders, Web3 addresses and `email-code`
sign-ups, the application decides:

```kotlin
authFlows(...) {
    registerUnknownAccounts(LDAP, VerifiableCredential) { identifier ->
        accounts.addAccountIdentifierToAccount(Uuid.random().toString(), identifier)
    }
}
```

The tenant is known there too (`currentAuthnzTenant()`), e.g. to register only for some tenants.

## Email codes

`email-code` sends a one-time code through your mail service:

```kotlin
install(KtorAuthnz) {
    emailCodes = EmailCodeSettings { delivery -> mailer.send(delivery.email ?: emailOf(delivery.accountId!!), delivery.code) }
}
```

As a later step, `POST {sessionId}/email-code/send` sends a code for the account (`delivery.accountId`) and
`POST {sessionId}/email-code {"code": ...}` checks it. As the first step it is a passwordless login:
`POST email-code/send {"email": ...}` (the same answer whether or not the address has an account).

## Current limits

- `KtorAuthnz` is configured once per JVM: tenants share the token handler, session store and cookie settings.
- Passkeys are bound to a domain. Tenants on their own domains each need their own relying party:

  ```kotlin
  install(KtorAuthnz) {
      passkeysPerTenant { tenant -> PasskeySettings(rpId = "$tenant.example.com", rpName = tenant!!, origins = setOf("https://$tenant.example.com")) }
  }
  ```

  Passkey challenges answer only in the tenant they were issued for.
- A user who has not enrolled TOTP cannot pass a flow that requires it; enrolment happens after login
  (`totpEnrollment`).
