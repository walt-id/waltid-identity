package id.walt.wallet2

import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureConfig
import id.walt.commons.testing.E2ETest
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.did.dids.DidService
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.wallet2.auth.RegisterRequest
import id.walt.wallet2.auth.configureWallet2Auth
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
import id.walt.ktorauthnz.methods.OIDC
import id.walt.ktorauthnz.methods.config.OidcAuthConfiguration
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
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
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.ktor.server.application.install
import kotlinx.serialization.json.buildJsonArray


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
    fun testOidcAuthenticationFlow() {
        val oidcPort = 17051
        val idpPort = 17052

        val idpBase = "http://$host:$idpPort"
        val idpKid = "wallet2-test-idp-key"
        val oidcSubject = "oidc-user-1"

        val signingKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }

        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

        val idpKey = runBlocking {
            runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId(idpKid),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            )
        }

        val publicEncoded = runBlocking {
            requireNotNull(idpKey.capabilities.publicKeyExporter)
                .exportPublicKey()
                .toPublicJwk(idpKey.spec)
        }

        val publicJwk = Json
            .parseToJsonElement(
                publicEncoded.data.toByteArray().decodeToString()
            )
            .jsonObject
            .let { jwk ->
                buildJsonObject {
                    jwk.forEach { (name, value) ->
                        put(name, value)
                    }
                    put("kid", JsonPrimitive(idpKid))
                }
            }

        var expectedNonce: String? = null
        var expectedState: String? = null

        val oidcConfig = OidcAuthConfiguration(
            openIdConfiguration = OIDC.OpenIdConfiguration(
                issuer = idpBase,
                authorizationEndpoint = "$idpBase/authorize",
                tokenEndpoint = "$idpBase/token",
                userinfoEndpoint = "$idpBase/userinfo",
                jwksUri = "$idpBase/jwks",
                idTokenSigningAlgValuesSupported = listOf("ES256"),
            ),
            clientId = "wallet2-test",
            clientSecret = "wallet2-secret",
            callbackUri = "http://$host:$oidcPort/auth/oidc/callback",
            pkceEnabled = true,
        )

        val authConfig = OSSWallet2AuthConfig(
            signingKey = DirectSerializedKey(signingKey),
            tokenExpiry = 1.hours,
            oidc = oidcConfig,
        )

        val idpServer = embeddedServer(
            CIO,
            host = host,
            port = idpPort,
        ) {
            install(ContentNegotiation) {
                json()
            }

            routing {
                post("/token") {
                    val params = call.receiveParameters()

                    assertEquals(
                        "authorization_code",
                        params["grant_type"]
                    )
                    assertEquals(
                        "test-code",
                        params["code"]
                    )
                    assertEquals(
                        "http://$host:$oidcPort/auth/oidc/callback",
                        params["redirect_uri"]
                    )
                    assertTrue(
                        !params["code_verifier"].isNullOrBlank(),
                        "OIDC token request must contain PKCE code_verifier"
                    )

                    val nonce = requireNotNull(expectedNonce) {
                        "Expected nonce was not captured before token exchange"
                    }

                    val now = Clock.System.now()

                    val idTokenPayload = buildJsonObject {
                        put("iss", JsonPrimitive(idpBase))
                        put("sub", JsonPrimitive(oidcSubject))
                        put("aud", JsonPrimitive("wallet2-test"))
                        put(
                            "exp",
                            JsonPrimitive((now + 5.minutes).epochSeconds)
                        )
                        put("iat", JsonPrimitive(now.epochSeconds))
                        put("nonce", JsonPrimitive(nonce))
                        put("sid", JsonPrimitive("oidc-session-1"))
                    }

                    val idToken = CompactJws.sign(
                        payload = idTokenPayload.toString().encodeToByteArray(),
                        key = idpKey,
                        algorithm = JwsAlgorithm.ES256,
                        protectedHeader = buildJsonObject {
                            put("kid", JsonPrimitive(idpKid))
                        },
                    )

                    call.respond(
                        buildJsonObject {
                            put("id_token", JsonPrimitive(idToken))
                            put(
                                "access_token",
                                JsonPrimitive("test-access-token")
                            )
                            put("token_type", JsonPrimitive("Bearer"))
                        }
                    )
                }

                get("/jwks") {
                    call.respond(
                        buildJsonObject {
                            put(
                                "keys",
                                buildJsonArray {
                                    add(publicJwk)
                                }
                            )
                        }
                    )
                }

                get("/userinfo") {
                    call.respond(
                        buildJsonObject {
                            put("sub", JsonPrimitive(oidcSubject))
                            put(
                                "email",
                                JsonPrimitive("oidc-user@example.com")
                            )
                        }
                    )
                }
            }
        }.start(wait = false)

        try {
            OSSWallet2Service.configureInMemory()

            E2ETest(host, oidcPort, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                featureAmendments = mapOf(
                    CommonsFeatureCatalog.authenticationServiceFeature to suspend {
                        AuthenticationServiceModule.AuthenticationServiceConfig.customAuthentication = {
                            ktorAuthnz("ktor-authnz") { }
                        }
                    }
                ),
                preload = {
                    ConfigManager.preloadConfig(
                        "_features",
                        FeatureConfig(enabledFeatures = listOf("auth"))
                    )
                    ConfigManager.preloadConfig(
                        "wallet-service",
                        OSSWallet2ServiceConfig(
                            publicBaseUrl = Url("http://$host:$oidcPort")
                        )
                    )
                    ConfigManager.preloadConfig("auth", authConfig)
                },
                init = {
                    DidService.minimalInit()
                },
                module = {
                    val loadedAuthConfig = runBlocking { configureWallet2Auth() }
                    wallet2Module(
                        withPlugins = false,
                        authConfig = loadedAuthConfig
                    )
                },
            ) {
                val http = testHttpClient()

                testAndReturn("OIDC authentication endpoint returns configured authorization URL") {
                    val response = http.get("/auth/oidc/auth")
                        .also { assertEquals(HttpStatusCode.OK, it.status) }

                    val session = response.body<AuthSessionInformation>()

                    assertEquals(AuthSessionStatus.CONTINUE_NEXT_STEP, session.status)
                    assertEquals(OIDC.id, session.currentlyActiveMethod)

                    val redirect = session.nextStep as? AuthSessionNextStepRedirectData
                    assertNotNull(redirect, "Expected OIDC authentication to return a redirect next step")

                    val authUrl = redirect.url

                    assertEquals(URLProtocol.HTTP, authUrl.protocol)
                    assertEquals(host, authUrl.host)
                    assertEquals(idpPort, authUrl.port)
                    assertEquals("/authorize", authUrl.encodedPath)

                    assertEquals("code", authUrl.parameters["response_type"])
                    assertEquals("openid profile email", authUrl.parameters["scope"])
                    assertEquals("wallet2-test", authUrl.parameters["client_id"])
                    assertEquals(
                        "http://$host:$oidcPort/auth/oidc/callback",
                        authUrl.parameters["redirect_uri"]
                    )

                    assertTrue(
                        !authUrl.parameters["state"].isNullOrBlank(),
                        "OIDC authorization URL must contain a state parameter"
                    )
                    assertTrue(
                        !authUrl.parameters["nonce"].isNullOrBlank(),
                        "OIDC authorization URL must contain a nonce parameter"
                    )

                    expectedState = authUrl.parameters["state"]
                    expectedNonce = authUrl.parameters["nonce"]

                    assertTrue(
                        !authUrl.parameters["code_challenge"].isNullOrBlank(),
                        "OIDC authorization URL must contain a PKCE code challenge"
                    )
                    assertEquals(
                        "S256",
                        authUrl.parameters["code_challenge_method"]
                    )
                }
                val state = requireNotNull(expectedState) {
                    "OIDC state was not captured from authorization URL"
                }

                val callbackSession = testAndReturn("OIDC callback issues Wallet2 JWT") {
                    http.get("/auth/oidc/callback") {
                        parameter("code", "test-code")
                        parameter("state", state)
                    }
                        .also {
                            assertEquals(HttpStatusCode.OK, it.status)
                        }
                        .body<AuthSessionInformation>()
                }

                assertEquals(
                    AuthSessionStatus.SUCCESS,
                    callbackSession.status
                )

                val token = assertNotNull(
                    callbackSession.token,
                    "OIDC authentication succeeded but Wallet2 JWT was not issued"
                )

                assertEquals(
                    3,
                    token.split(".").size,
                    "Expected Wallet2 authentication token to be a JWT"
                )

                testAndReturn("OIDC Wallet2 JWT authenticates account requests") {
                    http.get("/auth/account/wallets") {
                        bearerAuth(token)
                    }.also {
                        assertEquals(HttpStatusCode.OK, it.status)
                    }
                }
            }
        } finally {
            idpServer.stop(
                gracePeriodMillis = 1000,
                timeoutMillis = 3000,
            )
        }
    }

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
        OSSWallet2Service.configureInMemory()

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
                // Load this through configureWallet2Auth(), as production startup does.
                ConfigManager.preloadConfig("auth", authConfig)
            },
            init = { DidService.minimalInit() },
            module = {
                val loadedAuthConfig = runBlocking { configureWallet2Auth() }
                wallet2Module(withPlugins = false, authConfig = loadedAuthConfig)
            },
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
                val payloadJson = b64Payload.decodeFromBase64Url().decodeToString()
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

            testAndReturn("Named stores are not exposed in authenticated mode") {
                http.get("/stores/keys") { bearerAuth(token) }
                    .also { assertEquals(HttpStatusCode.NotFound, it.status) }
            }

            testAndReturn("Authenticated wallet cannot attach a global named store") {
                http.post("/wallet") {
                    bearerAuth(token)
                    setBody(CreateWalletRequest(keyStoreIds = listOf("foreign-store")))
                }.also { assertEquals(HttpStatusCode.BadRequest, it.status) }
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
            testAndReturn("Second account cannot link first account's wallet → 403") {
                http.post("/auth/account/wallets/$walletId") {
                    bearerAuth(token2)
                }.also { assertEquals(HttpStatusCode.Forbidden, it.status) }
            }
            testAndReturn("Second account cannot delete first account's wallet → 403") {
                http.delete("/wallet/$walletId") {
                    bearerAuth(token2)
                }.also { assertEquals(HttpStatusCode.Forbidden, it.status) }
                http.get("/wallet/$walletId") {
                    bearerAuth(token)
                }.also { assertEquals(HttpStatusCode.OK, it.status) }
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
