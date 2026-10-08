package id.walt

import com.webauthn4j.data.*
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor
import com.webauthn4j.test.client.ClientPlatform
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.enrollment.passkeyEnrollment
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.config.PasskeySettings
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Passkey registration and login, with webauthn4j's authenticator emulator as the browser and authenticator. */
class PasskeyTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    private val origin = "https://app.example.com"
    private val alice = "11111111-1111-1111-1111-000000000000"

    @AfterTest
    fun off() {
        KtorAuthnzManager.passkeys = null
    }

    private fun passkeyTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = ExampleAccountStore
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
                passkeys = PasskeySettings(rpId = "example.com", rpName = "Example", origins = setOf(origin))
            }
            install(Authentication) { ktorAuthnz("authnz") }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? id.walt.errors.HttpStatusError)?.status?.let(HttpStatusCode::fromValue)
                        ?: if (cause is IllegalArgumentException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing {
                route("login") {
                    authFlows(listOf(AuthFlow(method = "userpass", success = true), AuthFlow(method = "passkey", success = true)))
                }
                authenticate("authnz") { passkeyEnrollment { "alice@example.com" } }
            }
        }
        block()
    }

    /** The browser and an authenticator (without attestation, as requested) holding the passkeys. */
    private val browser = ClientPlatform(Origin(origin), WebAuthnAuthenticatorAdaptor(NoneAttestationAuthenticator()))

    private fun String.bytes() = b64.decode(this)
    private fun ByteArray.b64() = b64.encode(this)

    private fun create(options: JsonObject): String {
        val user = options["user"]!!.jsonObject
        val credential = browser.create(
            PublicKeyCredentialCreationOptions(
                PublicKeyCredentialRpEntity(options["rp"]!!.jsonObject["id"]!!.jsonPrimitive.content, "Example"),
                PublicKeyCredentialUserEntity(user["id"]!!.jsonPrimitive.content.bytes(), user["name"]!!.jsonPrimitive.content, "Alice"),
                DefaultChallenge(options["challenge"]!!.jsonPrimitive.content.bytes()),
                listOf(PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256)),
                60_000L, emptyList(),
                AuthenticatorSelectionCriteria(null, true, ResidentKeyRequirement.REQUIRED, UserVerificationRequirement.PREFERRED),
                emptyList(), AttestationConveyancePreference.NONE, null,
            )
        )
        val response = credential.response!!
        return buildJsonObject {
            put("id", credential.id); put("rawId", credential.rawId.b64()); put("type", "public-key")
            putJsonObject("response") {
                put("clientDataJSON", response.clientDataJSON.b64())
                put("attestationObject", response.attestationObject.b64())
                putJsonArray("transports") {}
            }
            putJsonObject("clientExtensionResults") {}
        }.toString()
    }

    private fun get(options: JsonObject): String {
        val credential = browser.get(
            PublicKeyCredentialRequestOptions(
                DefaultChallenge(options["challenge"]!!.jsonPrimitive.content.bytes()), 60_000L,
                options["rpId"]!!.jsonPrimitive.content, emptyList(), UserVerificationRequirement.PREFERRED, null,
            )
        )
        val response = credential.response!!
        return buildJsonObject {
            put("id", credential.id); put("rawId", credential.rawId.b64()); put("type", "public-key")
            putJsonObject("response") {
                put("clientDataJSON", response.clientDataJSON.b64())
                put("authenticatorData", response.authenticatorData.b64())
                put("signature", response.signature.b64())
                response.userHandle?.let { put("userHandle", it.b64()) }
            }
            putJsonObject("clientExtensionResults") {}
        }.toString()
    }

    private suspend fun ApplicationTestBuilder.options(path: String, token: String? = null) =
        json.parseToJsonElement(client.post(path) { token?.let { bearerAuth(it) } }.bodyAsText()).jsonObject

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null) =
        client.post(path) { token?.let { bearerAuth(it) }; contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun ApplicationTestBuilder.register() {
        val token = assertNotNull(json.decodeFromString<AuthSessionInformation>(
            postJson("/login/userpass", """{"username":"alice1","password":"123456"}""").bodyAsText()
        ).token)
        val registered = postJson("/passkey/register?name=laptop", create(options("/passkey/register/options", token)), token)
        assertEquals(HttpStatusCode.Created, registered.status, registered.bodyAsText())
    }

    @Test
    fun `a registered passkey logs in its account, without a username`() = passkeyTest {
        register()
        val login = postJson("/login/passkey", get(options("/login/passkey/options")))
        assertEquals(HttpStatusCode.OK, login.status, login.bodyAsText())
        val token = assertNotNull(json.decodeFromString<AuthSessionInformation>(login.bodyAsText()).token)
        assertEquals(alice, KtorAuthnzManager.tokenHandler.getTokenAccountId(token))
    }

    @Test
    fun `a passkey response is accepted once`() = passkeyTest {
        register()
        val assertion = get(options("/login/passkey/options"))
        assertEquals(HttpStatusCode.OK, postJson("/login/passkey", assertion).status)
        assertEquals(HttpStatusCode.Unauthorized, postJson("/login/passkey", assertion).status, "replayed")
    }

    @Test
    fun `a response for a challenge of another purpose is refused`() = passkeyTest {
        register()
        val token = assertNotNull(json.decodeFromString<AuthSessionInformation>(
            postJson("/login/userpass", """{"username":"alice1","password":"123456"}""").bodyAsText()
        ).token)
        val registrationChallenge = options("/passkey/register/options", token)
        val forged = get(buildJsonObject { put("challenge", registrationChallenge["challenge"]!!); put("rpId", "example.com") })
        assertEquals(HttpStatusCode.Unauthorized, postJson("/login/passkey", forged).status)
    }
}
