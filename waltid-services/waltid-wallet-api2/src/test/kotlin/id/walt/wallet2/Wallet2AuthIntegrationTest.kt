package id.walt.wallet2

import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureConfig
import id.walt.commons.testing.E2ETest
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import id.walt.wallet2.auth.OSSWallet2AccountStore
import id.walt.wallet2.auth.RegisterRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.ktorauthnz.auth.ktorAuthnz
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the OSS Wallet2 optional auth feature.
 *
 * Verifies the full auth lifecycle:
 * 1. Register an account
 * 2. Login and obtain a JWT token (not an opaque token)
 * 3. JWT carries an `exp` claim matching [OSSWallet2AuthConfig.tokenExpiry]
 * 4. Create a wallet while authenticated → wallet is auto-linked to account
 * 5. List wallets for account → created wallet is present
 * 6. Access wallet with valid token → 200
 * 7. Access wallet without token → 401
 * 8. Second account cannot access first account's wallet → 403
 * 9. Logout returns 200 (JWT tokens are stateless; exp governs validity)
 */
class Wallet2AuthIntegrationTest {

    private val host = "127.0.0.1"
    private val port = 17050

    @Test
    fun testAuthFlow() {
        // Generate a fresh secp256r1 key for this test run.
        // In production, operators generate once and embed in config so all replicas share the same key.
        val signingKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }

        val authConfig = OSSWallet2AuthConfig(
            signingKey = DirectSerializedKey(signingKey),
            tokenExpiry = 1.hours  // shorter than default to make expiry observable in test
        )

        // Reset the in-memory wallet store between tests so port reuse is safe
        OSSWallet2Service.walletStore = id.walt.wallet2.stores.inmemory.InMemoryWalletStore()

        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            featureAmendments = mapOf(
                // Set customAuthentication before AuthenticationServiceModule.enable() runs.
                CommonsFeatureCatalog.authenticationServiceFeature to suspend {
                    AuthenticationServiceModule.AuthenticationServiceConfig.customAuthentication = {
                        ktorAuthnz("ktor-authnz") { }
                    }
                }
            ),
            preload = {
                ConfigManager.preloadConfig("_features", FeatureConfig(enabledFeatures = listOf("auth")))
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
                // Preload so FeatureManager picks it up; the actual wiring happens in init below.
                ConfigManager.preloadConfig("auth", authConfig)
            },
            init = {
                DidService.minimalInit()
                // Wire JwtTokenHandler directly - mirrors what configureWallet2Auth() does at runtime.
                // The same key is used here so tokens issued by the test server are verifiable.
                KtorAuthnzManager.accountStore = OSSWallet2AccountStore
                KtorAuthnzManager.tokenHandler = JwtTokenHandler().apply {
                    this.signingKey = signingKey
                    verificationKey = signingKey
                }
            },
            // wallet2Module is non-suspend; authConfig is passed in so registerWallet2AuthRoutes
            // uses the configured tokenExpirySeconds without requiring another ConfigManager call.
            module = { wallet2Module(withPlugins = false, authConfig = authConfig) }
        ) {
            val http = testHttpClient()

            val email = "alice@example.com"
            val password = "correct-horse-battery-staple"

            // 1. Register
            testAndReturn("Register account") {
                http.post("/auth/register") {
                    setBody(RegisterRequest(email = email, password = password))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
            }

            // 2. Login → obtain JWT token
            val token = testAndReturn("Login → JWT token issued") {
                http.post("/auth/emailpass") {
                    setBody(mapOf("email" to email, "password" to password))
                }.also { assertEquals(HttpStatusCode.OK, it.status) }
                    .body<AuthSessionInformation>()
                    .also {
                        assertEquals(AuthSessionStatus.SUCCESS, it.status)
                        assertNotNull(it.token, "Login succeeded but no token was returned")
                        // Verify it is a JWT (three dot-separated Base64url segments)
                        val parts = it.token!!.split(".")
                        assertEquals(3, parts.size, "Expected a 3-part JWT, got: ${it.token}")
                    }
                    .token!!
            }

            // 3. Verify JWT exp claim matches tokenExpiry (within 30 s execution tolerance)
            testAndReturn("JWT carries exp claim matching tokenExpiry") {
                val b64Payload = token.split(".")[1]
                val padded = b64Payload + "=".repeat((4 - b64Payload.length % 4) % 4)
                val payloadJson = java.util.Base64.getUrlDecoder().decode(padded).decodeToString()
                val payload = Json.parseToJsonElement(payloadJson).jsonObject
                assertNotNull(payload["exp"], "JWT payload must contain 'exp' claim")
                val expEpoch = payload["exp"]!!.toString().toLong()
                val nowEpoch = System.currentTimeMillis() / 1000
                val delta = (expEpoch - nowEpoch).seconds
                val tolerance = 30.seconds
                assertTrue(
                    delta in (1.hours - tolerance)..(1.hours + tolerance),
                    "exp delta=$delta, expected ~1h (tokenExpiry=1h)"
                )
            }

            // 4. Create wallet while authenticated → auto-linked to account
            val walletId = testAndReturn("Create wallet (authenticated)") {
                http.post("/wallet") {
                    bearerAuth(token)
                    setBody(CreateWalletRequest())
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletCreatedResponse>().walletId
            }

            // 5. Account info → wallet appears in owned wallet list
            testAndReturn("Wallet listed under account") {
                val walletIds = http.get("/auth/account/wallets") {
                    bearerAuth(token)
                }.also { assertEquals(HttpStatusCode.OK, it.status) }
                    .body<List<String>>()
                assertTrue(walletId in walletIds, "Expected $walletId in $walletIds")
            }

            // 6. Access wallet with valid token → 200
            testAndReturn("Access wallet with valid token") {
                http.get("/wallet/$walletId") {
                    bearerAuth(token)
                }.also { assertEquals(HttpStatusCode.OK, it.status) }
            }

            // 7. Access wallet without token → 401
            testAndReturn("Access wallet without token → 401") {
                http.get("/wallet/$walletId")
                    .also { assertEquals(HttpStatusCode.Unauthorized, it.status) }
            }

            // 8. Register a second account; verify it cannot access the first account's wallet
            val email2 = "bob@example.com"
            val password2 = "hunter2"
            testAndReturn("Register second account") {
                http.post("/auth/register") {
                    setBody(RegisterRequest(email = email2, password = password2))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
            }
            val token2 = testAndReturn("Login as second account") {
                http.post("/auth/emailpass") {
                    setBody(mapOf("email" to email2, "password" to password2))
                }.body<AuthSessionInformation>().token!!
            }
            testAndReturn("Second account cannot access first account's wallet → 403") {
                http.get("/wallet/$walletId") {
                    bearerAuth(token2)
                }.also { assertEquals(HttpStatusCode.Forbidden, it.status) }
            }

            // 9. Logout returns 200 (JWTs are stateless; the token remains valid until exp)
            testAndReturn("Logout returns 200") {
                http.post("/auth/logout") {
                    bearerAuth(token)
                }.also { assertEquals(HttpStatusCode.OK, it.status) }
            }
        }
    }
}
