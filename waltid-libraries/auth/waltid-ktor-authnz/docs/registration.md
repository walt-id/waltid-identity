# Registration

Accounts come into being in three ways, all through `registerAccount`, so one hook sees every new account:

| how | what creates it |
|---|---|
| the user signs up with a password | the `signUp` routes |
| a first login with an identity no account has yet | OIDC always; LDAP, VC, Web3 and `email-code` with `registerUnknownAccounts(...)` |
| the application (an admin page, an import, an invitation) | `registerAccount { ... }` |

## The hook

```kotlin
install(KtorAuthnz) {
    accountStore = accounts
    onAccountRegistered { account ->
        // account.accountId, account.identifiers, account.details (sign-up fields or OIDC claims)
        profiles.create(account.accountId, name = account.details?.get("name")?.jsonPrimitive?.contentOrNull)
    }
}
```

The login identifiers and their data are stored before the hook runs; failing the hook fails the registration.

## Sign-up routes

```kotlin
routing {
    route("auth") {
        authFlows(flows)
        signUp(EmailPass)                       // or signUp(UserPass)
    }
}
```

With `EmailPass`, the address is verified first (`verifyEmail`, on by default; needs `emailCodes`):

```http
POST /auth/signup            {"email": "alice@example.com", "password": "...", "name": "Alice"}
-> 202 {"signup_id": "..."}  and a code sent to alice@example.com

POST /auth/signup/confirm    {"signup_id": "...", "code": "123456"}
-> 201 {"account_id": "..."}
```

Without verification (`UserPass`, or `verifyEmail = false`), `POST signup` creates the account and answers 201 at
once. Fields besides the login name and password are the account's `details`, passed to the hook. The password is
hashed before it is stored - also while a sign-up waits for its code.

Refused: a login name that has an account (409), a password shorter than `minimumLength` (400), more than
`maxPerHour` sign-ups for one login name (429), and a sign-up after five wrong codes. Rate limiting per client (e.g.
per IP address) is up to the application.

## From code

```kotlin
val accountId = registerAccount(details = buildJsonObject { put("invitedBy", adminId) }) {
    password(EmailIdentifier("bob@example.com"), initialPassword)
    totp(secret)                               // set up beforehand
    identifier(OIDCIdentifier(issuer, subject)) // logs in with the company IdP too
    loginFlows(setOf(...))                     // what `identify` offers this account
}
```

Every identifier is checked before anything is written: if one belongs to an account already, nothing is stored and
`AccountExistsException` (409) is thrown. Under an `authnzTenant` scope (or in `inAuthnzTenant`), the account belongs to
that tenant.
