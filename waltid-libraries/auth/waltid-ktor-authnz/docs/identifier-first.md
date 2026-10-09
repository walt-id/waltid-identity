# Identifier-first login

The user enters an email address (or username) first; the answer lists the ways *this* account can log in, and the
login continues with one of them. Accounts differ in what their users set up: one has a password then TOTP, or an
email code; another a passkey or a wallet credential; another the company identity provider, or the directory then
TOTP. The complete, compiled and tested example is
[`examples/identifierfirst`](../src/test/kotlin/id/walt/ktorauthnz/examples/identifierfirst/IdentifierFirstApp.kt).

## The flow

```json
{"method": "identify", "config": {
  "methods": {
    "oidc": {"openIdConfigurationUrl": "https://idp.example.org/.well-known/openid-configuration",
             "clientId": "...", "clientSecret": "...", "callbackUri": "https://app.example.org/auth/oidc/callback"},
    "ldap": {"ldapServerUrl": "ldaps://ldap.example.org", "userDNFormat": "mail=%s,ou=people,dc=example"}
  },
  "domains": {"example.net": [{"method": "oidc", "success": true}]},
  "default": [{"method": "email", "success": true}]
}}
```

`identify` has no `continue`: what follows depends on the account. It offers, in order:

1. `domains`: for an address of one of these domains, its flows - e.g. everyone at `example.net` logs in with its
   identity provider, with or without an account here (OIDC creates one on the first login);
2. the account's own flows, stored as `IdentifyStoredData` under the `identify` method for the account;
3. `default`, for accounts without flows of their own;
4. `unknown`, for identifiers without an account (e.g. a sign-up with `email-code`); without it they answer 404.

`methods` holds the settings of methods whose steps carry none - the identity provider, the directory, the verifier -
so accounts' flows only name the method.

## Routes

```kotlin
route("auth") {
    authFlows(
        methods = listOf(Identify, EmailPass, TOTP, EmailCode, Passkey, VerifiableCredential, OIDC, LDAP),
        firstMethods = listOf(Identify),
        flowsFor = { listOf(loginFlow) },
    )
}
```

List in `methods` every method an account may set up; `authFlows(listOf(loginFlow))` alone serves only the methods
the `identify` configuration names.

```http
POST /auth/identify                {"email": "user1@example.org"}
-> {"session_id": "s1", "status": "CONTINUE_NEXT_FLOW", "next_method": ["email", "email-code"]}

POST /auth/s1/email                {"password": "..."}
-> {"session_id": "s1", "status": "CONTINUE_NEXT_FLOW", "next_method": ["totp"]}

POST /auth/s1/totp                 {"code": "123456"}
-> {"session_id": "s1", "status": "SUCCESS", "token": "..."}
```

## What is stored where

| where | what | in the example |
|---|---|---|
| the flow (`config`) | settings shared by all accounts | the `identify` configuration: IdP, directory, verifier, the `example.net` rule |
| per account | what the user set up, and account-bound secrets | `IdentifyStoredData` (the offered flows), TOTP secret |
| per identifier | identifier-bound secrets | the password (under the email), each passkey (under its credential id) |
| identifiers of the account | the other identities that log into it | its OIDC subject, LDAP name, wallet DID |
| per session | the progress of one login | the identified address (`IdentifiedSessionData`) and the account |

When a user adds or removes a method, the application updates the account's `IdentifyStoredData` along with the
method's data.

## After identify

- Password steps (`email`, `userpass`, `ldap`, `radius`) need only the password; a different login name is refused.
- `email-code` sends to the identified address (`EmailCodeDelivery.email`).
- OIDC passes the address to the identity provider as `login_hint`.
- Every step must authenticate the identified account: a valid passkey, wallet credential or IdP login of *another*
  account is refused (401), so no account can be reached through the flows of another one. This holds for all
  multi-step flows, not only after `identify`.
