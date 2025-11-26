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

In this example, we add a new Auth Method (specifically a: global (= defined by flow instead of user), implicit-allowed (= doesn't require
explicit session start), **multi-step** (= see below)) method.

This is a multi-step method, because it contains more than a single stage/step when using the auth method. This means:

- Some auth methods, like UserPass, consist of a single request (`POST /login {username=alice, password=123456}`), and that is the full
  login (the response to `/login` is already the auth token)
- More complex auth methods, like OIDC or challenge/response mechanisms, consist of multiple steps

Here we opt for an example demo of a very simple challenge/response mechanism, which will consist of two steps:
- GET /nonce
  - Retrieve a nonce (= *challenge*) to sign
- POST /signed
  - Post the signed nonce (= *response*)

### Identifier

```kotlin
package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName

@Serializable
@SerialName("multistep-example")
data class MultiStepExampleIdentifier(
    val publicKey: String
) : AccountIdentifier() {
    override fun identifierName() = "multistep-example" // SerialName and identifierName should match

    override fun toDataString() = publicKey // what is part of this identifier? In this case just the string "publicKey"

    companion object :
        AccountIdentifierFactory<MultiStepExampleIdentifier>("multistep-example") { // this creator id also has to match with identifierName
        override fun fromAccountIdentifierDataString(dataString: String) = MultiStepExampleIdentifier(dataString)

        val EXAMPLE = MultiStepExampleIdentifier("0xABCDEF0123456789") // Define a nice example for the docs
    }
}
```

Remember to add this new method to `AccountIdentifierManager.kt`:

```kotlin
private val defaultIdentifiers =
    listOf(
        EmailIdentifier, JWTIdentifier, LDAPIdentifier, OIDCIdentifier, RADIUSIdentifier, UsernameIdentifier,
        MultiStepExampleIdentifier // <-- new
    )
```

### Method

```kotlin
package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.MultiStepExampleIdentifier
import id.walt.ktorauthnz.exceptions.authCheck
import io.github.smiley4.ktoropenapi.post
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import kotlin.random.Random

object MultiStepExample : AuthenticationMethod("multistep-example") {

    fun makeNonce() = "n" + Random.nextInt() // should be a JWT

    fun verifiySignature(multiStepExampleSigned: MultiStepExampleSigned) {
        multiStepExampleSigned.challenge // check that the challenge comes from us (is a JWT made by us)

        // check that signed challenge verifies correctly
        authCheck(multiStepExampleSigned.signed == "${multiStepExampleSigned.challenge}-signed") { "Invalid signature" }

        // check that public key belongs to signature
        multiStepExampleSigned.publicKey
    }

    @Serializable
    data class MultiStepExampleSigned(
        val challenge: String,
        val signed: String,
        val publicKey: String
    )

    override fun Route.register(authContext: ApplicationCall.() -> AuthContext) {
        route("multistep-example") {
            get("nonce") { // Step 1
                call.respond(makeNonce())
            }

            post<MultiStepExampleSigned>("signed", { // Step 2
                request { body<MultiStepExampleSigned>() }
            }) { req ->
                val session = getSession(authContext)

                verifiySignature(req) // Verification

                // Verification was successful:

                val identifier = MultiStepExampleIdentifier(req.publicKey) // select identifier (= who logged in with this method now?)

                context.handleAuthSuccess(session, identifier.resolveToAccountId()) // handleAuthSuccess() -> session is now logged in
            }
        }
    }
}
```

### Add it to the example

To try out our new method, we have to use it somewhere.

Add it in the test/kotlin/id/walt `ExampleWeb.kt` to try it out:

```kotlin
@file:OptIn(ExperimentalUuidApi::class)

package id.walt

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.auth.getAuthToken
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.*
import id.walt.ktorauthnz.sessions.SessionManager
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.intellij.lang.annotations.Language
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun Route.globalMultistepExample() {
    route("global-multistep-example") {
        @Language("JSON")
        val flowConfig = """
        {
            "method": "multistep-example",
            "success": true
        }
    """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)

        val contextFunction: ApplicationCall.() -> AuthContext = {
            AuthContext(
                tenant = call.request.host(),
                sessionId = call.parameters["sessionId"],
                implicitSessionGeneration = true,
                initialFlow = authFlow
            )
        }

        registerAuthenticationMethod(MultiStepExample, contextFunction)
    }
}

fun Route.authFlowRoutes() {
    globalMultistepExample()
}

fun Application.testApp() {
    install(Authentication) {
        ktorAuthnz("ktor-authnz") {

        }
    }


    routing {
        route("auth") {
            route("flows") {
                authFlowRoutes()
            }

            post<AccountIdentifier>("register-by-identifier") { identifier ->
                val newAccountId = Uuid.random().toString()
                KtorAuthnzManager.accountStore.addAccountIdentifierToAccount(newAccountId, identifier)
                call.respond(newAccountId)
            }
        }

        authenticate("ktor-authnz") {
            get("/protected") {
                val token = getAuthToken()
                val accountId = getAuthenticatedAccount()
                call.respondText("Hello token ${token}, you are $accountId")
            }
        }

    }
}
```

The new part here is mainly `fun Route.globalMultistepExample()`

## Usage of the newly implemented example

Some command line examples with [httpie](https://httpie.io/cli) (similar to curl):

```bash
### --- Register ---

# Register the account
http POST localhost:8088/auth/register-by-identifier type=multistep-example publicKey=0xABCDEF0123456789 
# Responds with:
897c98e5-0db2-4f09-95d0-2a3322e2ef3b # new account id

### --- Login ---

# Get challenge
http GET localhost:8088/auth/flows/global-multistep-example/multistep-example/nonce
# Responds with:
n1397597722  # this is the nonce to sign, would then become a JWT etc... see comments in MultistepExample auth method


# Invalid signature
http POST localhost:8088/auth/flows/global-multistep-example/multistep-example/signed challenge=n1397597722 signed=blahblah publicKey=0xABCDEF0123456789
# Responds with: 500: AuthenticationFailureException(message=Invalid signature)


# Correct signature
http POST localhost:8088/auth/flows/global-multistep-example/multistep-example/signed challenge=n1397597722 signed=n1397597722-signed publicKey=0xABCDEF0123456789
# Responds with:
{
    "session_id": "8b2ff5b4-f6bc-4390-8308-dbe57f177e1e",
    "status": "OK",
    "token": "7f2505a7-3ed0-47c0-b214-67c2c46e9f0b"
}

# Try to use authenticated route
http -v localhost:8088/protected -A bearer -a 7f2505a7-3ed0-47c0-b214-67c2c46e9f0b
# Responds with:
Hello token 7f2505a7-3ed0-47c0-b214-67c2c46e9f0b, you are 897c98e5-0db2-4f09-95d0-2a3322e2ef3b  # token and user id
```
