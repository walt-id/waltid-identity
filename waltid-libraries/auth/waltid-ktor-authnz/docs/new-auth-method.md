# Example: Adding a new multi-step auth method

When adding a new auth method, you would usually add an Auth Identifier and an Auth Method.

## Short intro: identifier

The Identifier is what will reference an account, and is usually Auth Method specific.
For example:

- An account login with username & password would have a UsernamePasswordIdentifier
    - The UsernamePasswordIdentifier would probably use the username within itself
- An account login with RADIUS also uses username & password, but at a specific host
    - The RadiusIdentifier would probably use username + host within itself
        - Because bob at host company1.com is a different account than bob at host company2.com
- An account login with Webauthn works with a challenge/response system, and the identifier is the public key
    - The WebAuthnIdentifier would probably use the raw public key or the public key fingerprint within itself
        - (no usernames involved with this auth method)

However, in a special case, some authentication methods do not work on an account identifier, but on an account itself.
For example, certain 2FA (two-factor authentication) methods, like TOTP. TOTP has to know what account to work on, because
it has to compare the secret for a certain account. But TOTP login does not involve any account id, you just share the 6-digit pin.
For this reason, you have to have used a certain auth method prior to TOTP, so that an account can be selected (for example, you could first
use Email & password, and then use TOTP after that - with the first method, the account can be selected by email, and then the information
what secret to compare against exists for the TOTP method). So some methods, like TOTP, actually have no identifier (there is no
`TotpIdentifier` or similar).

## Short intro: method

The Auth Method is what holds the implementation that checks authentication.

- Of course, authentication with username & password works differently than with public key or TOTP
- Some methods have configuration (global to the flow), some methods have StoredData, and some have neither
- Configuration vs StoredData:
    - OIDC remote details (what OIDC server to authenticate against, client id & client secret, etc.) would be global to the flow (the same
      for all users of the flow) -> thus it is AuthMethodConfiguration
    - With the UserPass auth method, users are authenticated against their passwords. Of course, the passwords are not the same for
      everyone (and thus NOT global to the flow), but different for every user -> thus it is AuthMethodStoredData (stored for every user
      individually)

## Code example

A challenge/response method in two steps: `GET challenge` hands out a nonce, `POST signed` receives it signed.

### Identifier

```kotlin
@Serializable
@SerialName("multistep-example")
data class MultiStepExampleIdentifier(val publicKey: String) : AccountIdentifier() {
    override fun identifierName() = "multistep-example" // matches the SerialName
    override fun toDataString() = publicKey

    companion object : AccountIdentifierFactory<MultiStepExampleIdentifier>("multistep-example") {
        override fun fromAccountIdentifierDataString(dataString: String) = MultiStepExampleIdentifier(dataString)
    }
}
```

`AccountIdentifier` is sealed: identifiers live in `id.walt.ktorauthnz.accounts.identifiers.methods` of this library.
Add the factory to `AccountIdentifierManager`.

### Method

```kotlin
object MultiStepExample : AuthenticationMethod("multistep-example") {

    @Serializable
    data class Signed(val challenge: String, val signed: String, val publicKey: String)

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        route(id) { // routes are named by the method id, so clients follow `next_method`
            get("challenge") {
                // single use: remember it until it is answered
                val challenge = Uuid.random().toString()
                KtorAuthnzManager.expiringStore.put("multistep-challenge:$challenge", "issued", 5.minutes)
                call.respond(challenge)
            }

            post("signed") {
                val session = call.getAuthSession(authContext) // checks session state and attempt limits
                val request = call.receive<Signed>()

                authCheck(
                    KtorAuthnzManager.expiringStore.get("multistep-challenge:${request.challenge}") != null,
                    InvalidChallengeException()
                )
                KtorAuthnzManager.expiringStore.remove("multistep-challenge:${request.challenge}")
                // a failed check throws an AuthException: answered 401 and counted as a failed attempt
                if (!verify(request)) authFailure("Invalid signature")

                val accountId = MultiStepExampleIdentifier(request.publicKey).resolveToAccountId()
                call.handleAuthSuccess(session, authContext(call), accountId) // progresses the flow, responds
            }
        }
    }
}
```

Register it before serving flows that use it:

```kotlin
AuthMethodManager.registerAuthenticationMethod(MultiStepExample)

routing {
    route("auth") { authFlows(listOf(AuthFlow(method = "multistep-example", success = true))) }
}
```

- `GET /auth/multistep-example/challenge`, then `POST /auth/multistep-example/signed` logs in.
- Used as a later step of a flow, it is served at `/auth/{sessionId}/multistep-example/...`.

Guidelines:

- Name the account identifier with `call.attemptOnIdentifier(id, name)` before checking a credential, so that
  failures count per identifier and not only per session.
- Keep per-login state in the session (`session.setSessionData(...)`), not in memory.
- Emit nothing yourself for logins: `handleAuthSuccess` and failed `AuthException`s produce the `AuthnzEvent`s.
