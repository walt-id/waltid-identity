package id.walt.ktorauthnz.methods

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** A full OIDC login against a mock identity provider on a local port. */
class OidcLoginTest {

    private val clientId = "authnz-test-client"

    /** Accounts by identifier; account ids are whatever the library hands to [addAccountIdentifierToAccount]. */
    private object Accounts : EditableAccountStore {
        val byIdentifier = ConcurrentHashMap<AccountIdentifier, String>()
        override suspend fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier) {
            byIdentifier[newAccountIdentifier] = accountId
        }
        override suspend fun lookupAccountUuid(identifier: AccountIdentifier) = byIdentifier[identifier]
        override suspend fun removeAccountIdentifierFromAccount(accountIdentifier: AccountIdentifier) {}
        override suspend fun addAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData) {}
        override suspend fun addAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) {}
        override suspend fun updateAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData) {}
        override suspend fun updateAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) {}
        override suspend fun deleteAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String) {}
        override suspend fun deleteAccountStoredData(accountId: String, method: String) {}
        override suspend fun lookupStoredDataForAccount(accountId: String, method: AuthenticationMethod) = null
        override suspend fun lookupStoredDataForAccountIdentifier(identifier: AccountIdentifier, method: AuthenticationMethod) = null
        override suspend fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod) = false
    }

    /** A mock IdP: discovery, token (ID token for [subject], with [sid] if set), userinfo, JWKS, end session. */
    private inner class MockIdp {
        var subject = "12345"
        var sid: String? = null
        var nonce = ""

        private val key = runBlocking {
            CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(KeyId("idp-key"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
            )
        }
        private val publicJwk = runBlocking {
            Json.parseToJsonElement(
                key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString()
            ).jsonObject.let { JsonObject(it + ("kid" to JsonPrimitive("idp-key"))) }
        }

        private val port = java.net.ServerSocket(0).use { it.localPort }

        val server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) { json() }
            routing {
                get("/.well-known/openid-configuration") {
                    val base = "http://127.0.0.1:${port()}"
                    call.respond(buildJsonObject {
                        put("issuer", base)
                        put("authorization_endpoint", "$base/authorize")
                        put("token_endpoint", "$base/token")
                        put("userinfo_endpoint", "$base/userinfo")
                        put("jwks_uri", "$base/jwks")
                        put("end_session_endpoint", "$base/logout")
                        putJsonArray("id_token_signing_alg_values_supported") { add("ES256") }
                    })
                }
                post("/token") {
                    val now = Clock.System.now().epochSeconds
                    val idToken = CompactJws.sign(
                        payload = buildJsonObject {
                            put("iss", "http://127.0.0.1:${port()}")
                            put("aud", clientId)
                            put("sub", subject)
                            put("nonce", nonce)
                            put("iat", now)
                            put("exp", now + 300)
                            sid?.let { put("sid", it) }
                        }.toString().encodeToByteArray(),
                        key = key,
                        algorithm = JwsAlgorithm.ES256,
                        protectedHeader = buildJsonObject { put("kid", "idp-key") },
                    )
                    call.respond(buildJsonObject {
                        put("id_token", idToken); put("access_token", "at"); put("token_type", "Bearer")
                    })
                }
                get("/userinfo") { call.respond(buildJsonObject { put("sub", subject) }) }
                get("/jwks") { call.respond(buildJsonObject { putJsonArray("keys") { add(publicJwk) } }) }
            }
        }.start(wait = false)

        fun port() = port
    }

    private lateinit var idp: MockIdp

    @BeforeTest
    fun setUp() {
        idp = MockIdp()
        Accounts.byIdentifier.clear()
        KtorAuthnzManager.accountStore = Accounts
        KtorAuthnzManager.sessionStore = InMemorySessionStore()
        KtorAuthnzManager.expiringStore = InMemoryExpiringStore()
        KtorAuthnzManager.tokenHandler = KtorAuthNzTokenHandler()
    }

    @AfterTest
    fun tearDown() = idp.server.stop()

    private fun oidcTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val flow = AuthFlow.fromConfig(
            """
            {"method": "oidc", "success": true, "config": {
               "openIdConfigurationUrl": "http://127.0.0.1:${idp.port()}/.well-known/openid-configuration",
               "clientId": "$clientId", "clientSecret": "secret",
               "callbackUri": "http://localhost/oidc-flow/oidc/callback",
               "postLogoutRedirectUri": "http://localhost/bye"}}
            """.trimIndent()
        )
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            install(Authentication) { ktorAuthnz("ktor-authnz") {} }
            routing {
                route("oidc-flow") {
                    registerAuthenticationMethod(OIDC, {
                        AuthContext(sessionId = parameters["sessionId"], implicitSessionGeneration = true, initialFlow = flow)
                    })
                }
            }
        }
        block()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Runs the login and returns the finished session information. */
    private suspend fun ApplicationTestBuilder.login(): AuthSessionInformation {
        val started = json.decodeFromString<AuthSessionInformation>(client.get("/oidc-flow/oidc/auth").bodyAsText())
        val authUrl = (started.nextStep as AuthSessionNextStepRedirectData).url
        idp.nonce = assertNotNull(authUrl.parameters["nonce"])
        val state = assertNotNull(authUrl.parameters["state"])
        val callback = client.get("/oidc-flow/oidc/callback?code=c&state=$state")
        assertEquals(HttpStatusCode.OK, callback.status, callback.bodyAsText())
        return json.decodeFromString<AuthSessionInformation>(callback.bodyAsText())
    }

    @Test
    fun `a new OIDC identity gets a fresh account id, not the IdP subject`() = oidcTest {
        idp.subject = "not-a-uuid-subject"
        val first = login()
        assertEquals(AuthSessionStatus.SUCCESS, first.status)

        val accountId = Accounts.byIdentifier.values.single()
        assertNotEquals(idp.subject, accountId)
        Uuid.parse(accountId) // a store-format id

        login()
        assertEquals(listOf(accountId), Accounts.byIdentifier.values.toList(), "the same identity logs into the same account")
    }

    @Test
    fun `the IdP may omit sid`() = oidcTest {
        idp.sid = null
        assertEquals(AuthSessionStatus.SUCCESS, login().status)
    }

    @Test
    fun `RP-initiated logout names the client and the post-logout redirect`() = oidcTest {
        val token = assertNotNull(login().token)
        val logout = client.get("/oidc-flow/oidc/logout") { bearerAuth(token) }
        val endSessionUrl = Url(assertNotNull(json.parseToJsonElement(logout.bodyAsText()).jsonObject["end_session_url"]).jsonPrimitive.content)

        assertEquals(clientId, endSessionUrl.parameters["client_id"])
        assertEquals("http://localhost/bye", endSessionUrl.parameters["post_logout_redirect_uri"])
        assertTrue(endSessionUrl.parameters["id_token_hint"] != null)
    }
}
