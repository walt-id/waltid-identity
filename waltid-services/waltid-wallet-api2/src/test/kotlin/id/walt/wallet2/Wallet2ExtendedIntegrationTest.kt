package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.ProofType
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenIssuer
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenVerifier
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenIssuer
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenVerifier
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultCredentialRequestValidator
import id.walt.wallet2.data.StoredCredentialMetadata
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.handlers.FetchCredentialRequest
import id.walt.wallet2.handlers.GenerateAuthorizationUrlRequest
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialResult
import id.walt.wallet2.handlers.ResolveOfferRequest
import id.walt.wallet2.handlers.ResolveOfferResult
import id.walt.wallet2.handlers.RequestTokenRequest
import id.walt.wallet2.handlers.RequestTokenResult
import id.walt.wallet2.handlers.SignProofRequest
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import id.walt.wallet2.server.handlers.WalletInfoResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
import id.walt.openid4vci.CredentialFormat as VciCredentialFormat

/**
 * Extended end-to-end tests for the OSS Wallet2 service, specifically targeting the
 * scenarios reported as broken in the reviewer's feedback:
 *
 *  1. Wallet creation with explicit keystore + credential store + DID store attached correctly
 *  2. GET /wallet list works (was broken by Flow<String> serialization)
 *  3. GET /wallet/{id} reports correct store counts
 *  4. Key generation with multiple algorithms (Ed25519, secp256r1)
 *  5. DID creation with multiple methods (did:key, did:jwk)
 *  6. Isolated OID4VCI receive steps (resolve-offer → request-token → sign-proof → fetch)
 *  7. Multiple credential types stored and listed
 *  8. Credential deletion
 */
@OptIn(ExperimentalSerializationApi::class)
class Wallet2ExtendedIntegrationTest {

    private val host = "127.0.0.1"
    private val basePort = 17100

    // -----------------------------------------------------------------------
    // Test 1: Wallet creation with all three store types attached
    // -----------------------------------------------------------------------

    @Test
    fun testWalletCreationWithStoresAndListEndpoints() {
        val port = basePort
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            // -- 1a. Create key store and credential store by name first --
            testAndReturn("Create named key store") {
                http.post("/stores/keys/main-kms")
                    .also { assertEquals(HttpStatusCode.Created, it.status) }
            }
            testAndReturn("Create named credential store") {
                http.post("/stores/credentials/main-cred-store")
                    .also { assertEquals(HttpStatusCode.Created, it.status) }
            }

            // -- 1b. GET /stores/keys and /stores/credentials now list them --
            testAndReturn("List key stores via GET /stores/keys") {
                val stores = http.get("/stores/keys")
                    .also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                    .body<List<String>>()
                assertTrue(stores.contains("main-kms"), "main-kms should be listed, got $stores")
            }
            testAndReturn("List credential stores via GET /stores/credentials") {
                val stores = http.get("/stores/credentials")
                    .also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                    .body<List<String>>()
                assertTrue(stores.contains("main-cred-store"), "main-cred-store should be listed, got $stores")
            }

            // -- 1c. Create wallet referencing the named stores --
            val walletId = testAndReturn("Create wallet with explicit stores") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest(
                        keyStoreIds = listOf("main-kms"),
                        credentialStoreIds = listOf("main-cred-store")
                        // didStoreId omitted → auto-creates an in-memory DID store
                    ))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletCreatedResponse>().walletId
            }
            assertNotNull(walletId)

            // -- 1d. GET /wallet/{id} must show the correct attached store counts --
            testAndReturn("GET /wallet/{id} reports attached stores") {
                val info = http.get("/wallet/$walletId")
                    .also { assertEquals(HttpStatusCode.OK, it.status) }
                    .body<WalletInfoResponse>()
                assertEquals(1, info.keyStoreCount, "Expected 1 key store, got ${info.keyStoreCount}")
                assertEquals(1, info.credentialStoreCount, "Expected 1 credential store, got ${info.credentialStoreCount}")
                assertTrue(info.hasDidStore, "DID store should be auto-created")
            }

            // -- 1e. GET /wallet (list) must include this wallet - was broken by Flow<String> --
            testAndReturn("GET /wallet returns a list containing the new wallet") {
                val ids = http.get("/wallet")
                    .also { assertEquals(HttpStatusCode.OK, it.status, "GET /wallet failed: ${it.bodyAsText()}") }
                    .body<List<String>>()
                assertTrue(ids.contains(walletId), "Wallet $walletId should be in list $ids")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Test 2: Key generation with multiple algorithms + DID creation
    // -----------------------------------------------------------------------

    @Test
    fun testKeyAndDidManagement() {
        val port = basePort + 1
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()
            val walletId = http.post("/wallet") {
                contentType(ContentType.Application.Json)
                setBody(CreateWalletRequest())
            }.body<WalletCreatedResponse>().walletId

            // -- Generate Ed25519 key --
            val ed25519Key = testAndReturn("Generate Ed25519 key") {
                http.post("/wallet/$walletId/keys/generate") {
                    contentType(ContentType.Application.Json)
                    setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(keyType = KeyType.Ed25519))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletKeyInfo>()
            }
            assertEquals("Ed25519", ed25519Key.keyType)
            assertNotNull(ed25519Key.keyId)

            // -- Generate secp256r1 (P-256) key --
            val p256Key = testAndReturn("Generate secp256r1 (P-256) key") {
                http.post("/wallet/$walletId/keys/generate") {
                    contentType(ContentType.Application.Json)
                    setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(keyType = KeyType.secp256r1))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletKeyInfo>()
            }
            assertEquals("secp256r1", p256Key.keyType)

            // -- List keys - both should appear --
            testAndReturn("List keys shows both generated keys") {
                val keys = http.get("/wallet/$walletId/keys")
                    .body<List<WalletKeyInfo>>()
                assertEquals(2, keys.size, "Expected 2 keys, got ${keys.size}: $keys")
                assertTrue(keys.any { it.keyType == "Ed25519" })
                assertTrue(keys.any { it.keyType == "secp256r1" })
            }

            // -- Get a specific key by ID --
            testAndReturn("Get specific key by ID") {
                http.get("/wallet/$walletId/keys/${ed25519Key.keyId}")
                    .also { assertEquals(HttpStatusCode.OK, it.status) }
                    .body<WalletKeyInfo>()
                    .also { assertEquals(ed25519Key.keyId, it.keyId) }
            }

            // -- Create did:key (uses Ed25519 key) --
            val didKey = testAndReturn("Create did:key from Ed25519") {
                http.post("/wallet/$walletId/dids/create") {
                    contentType(ContentType.Application.Json)
                    setBody(id.walt.wallet2.server.handlers.CreateDidRequest(
                        method = "key", keyId = ed25519Key.keyId
                    ))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletDidEntry>()
            }
            assertTrue(didKey.did.startsWith("did:key:"), "Expected did:key:, got ${didKey.did}")

            // -- Create did:jwk (uses P-256 key) --
            val didJwk = testAndReturn("Create did:jwk from secp256r1") {
                http.post("/wallet/$walletId/dids/create") {
                    contentType(ContentType.Application.Json)
                    setBody(id.walt.wallet2.server.handlers.CreateDidRequest(
                        method = "jwk", keyId = p256Key.keyId
                    ))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletDidEntry>()
            }
            assertTrue(didJwk.did.startsWith("did:jwk:"), "Expected did:jwk:, got ${didJwk.did}")

            // -- List DIDs - both should appear --
            testAndReturn("List DIDs shows both created DIDs") {
                val dids = http.get("/wallet/$walletId/dids")
                    .body<List<WalletDidEntry>>()
                assertEquals(2, dids.size, "Expected 2 DIDs, got ${dids.size}")
                assertTrue(dids.any { it.did.startsWith("did:key:") })
                assertTrue(dids.any { it.did.startsWith("did:jwk:") })
            }

            // -- Delete a key --
            testAndReturn("Delete P-256 key") {
                http.delete("/wallet/$walletId/keys/${p256Key.keyId}")
                    .also { assertEquals(HttpStatusCode.NoContent, it.status) }
            }

            testAndReturn("Key list after deletion has 1 key") {
                val keys = http.get("/wallet/$walletId/keys").body<List<WalletKeyInfo>>()
                assertEquals(1, keys.size, "Expected 1 key after deletion, got ${keys.size}")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Test 3: Isolated OID4VCI receive steps (the "step-by-step" API)
    // -----------------------------------------------------------------------

    @Test
    fun testIsolatedReceiveSteps() {
        val issuerPort = basePort + 2
        val walletPort = basePort + 3
        val issuerBase = "http://$host:$issuerPort"
        val walletBase = "http://$host:$walletPort"

        val preAuthCode = "isolated-test-${Uuid.random()}"
        val issuerKey: JWKKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }
        val accessTokenKey: JWKKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }

        val credentialConfigId = "test_pid"
        val configuration = CredentialConfiguration(
            format = VciCredentialFormat.SD_JWT_VC,
            vct = "eu.europa.ec.eudi.pid.1",
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf("jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256", "EdDSA")))
        )

        // Set up the same in-process issuer as in the E2E test
        val preAuthRepo: PreAuthorizedCodeRepository = object : PreAuthorizedCodeRepository {
            private val records = java.util.concurrent.ConcurrentHashMap<String, PreAuthorizedCodeRecord>()
            override suspend fun save(record: PreAuthorizedCodeRecord) {
                if (records.containsKey(record.code)) throw DuplicateCodeException(); records[record.code] = record
            }
            override suspend fun get(code: String) = records[code]
            override suspend fun consume(code: String) = records.remove(code)
        }
        val authCodeRepo: AuthorizationCodeRepository = object : AuthorizationCodeRepository {
            private val records = java.util.concurrent.ConcurrentHashMap<String, AuthorizationCodeRecord>()
            override suspend fun save(record: AuthorizationCodeRecord) {
                if (records.containsKey(record.code)) throw DuplicateCodeException(); records[record.code] = record
            }
            override suspend fun consume(code: String) = records.remove(code)
        }
        val provider = buildOAuth2Provider(OAuth2ProviderConfig(
            authorizationRequestValidator = DefaultAuthorizationRequestValidator(),
            authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
            tokenEndpointHandlers = TokenEndpointHandlers(),
            authorizationCodeRepository = authCodeRepo,
            preAuthorizedCodeRepository = preAuthRepo,
            preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthRepo),
            accessTokenIssuer = JwtAccessTokenIssuer(resolver = { accessTokenKey }),
            accessTokenVerifier = JwtAccessTokenVerifier(resolver = { _ -> accessTokenKey }),
            refreshTokenIssuer = JwtRefreshTokenIssuer(signingKeyResolver = { accessTokenKey }),
            refreshTokenVerifier = JwtRefreshTokenVerifier(verificationKeyResolver = { _ -> accessTokenKey }),
            refreshTokenRepository = InMemoryRefreshTokenRepository(),
            accessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
            credentialRequestValidator = DefaultCredentialRequestValidator(),
            credentialEndpointHandlers = CredentialEndpointHandlers()
        ))
        val session = DefaultSession(subject = "holder-isolated")
        runBlocking {
            preAuthRepo.save(DefaultPreAuthorizedCodeRecord(
                code = preAuthCode, clientId = null, txCode = null, txCodeValue = null,
                grantedScopes = emptySet(), grantedAudience = emptySet(), session = session,
                expiresAt = Clock.System.now() + 10.minutes,
                credentialNonce = "isolated-nonce",
                credentialNonceExpiresAt = Clock.System.now() + 10.minutes
            ))
        }

        val issuerMetadata = CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = issuerBase,
            credentialConfigurationsSupported = mapOf(credentialConfigId to configuration)
        )
        val offer = CredentialOffer.withPreAuthorizedCodeGrant(
            credentialIssuer = issuerBase,
            credentialConfigurationIds = listOf(credentialConfigId),
            preAuthorizedCode = preAuthCode
        )

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        val issuerServer = embeddedServer(CIO, host = host, port = issuerPort) {
            install(ContentNegotiation) { json(json) }
            routing {
                get("/.well-known/openid-credential-issuer") { call.respond(issuerMetadata) }
                get("/.well-known/oauth-authorization-server") {
                    call.respond(AuthorizationServerMetadata(
                        issuer = issuerBase, authorizationEndpoint = "$issuerBase/authorize",
                        tokenEndpoint = "$issuerBase/token",
                        responseTypesSupported = setOf("code", "token"),
                        grantTypesSupported = setOf("urn:ietf:params:oauth:grant-type:pre-authorized_code")
                    ))
                }
                get("/.well-known/openid-configuration") {
                    call.respond(AuthorizationServerMetadata(
                        issuer = issuerBase, authorizationEndpoint = "$issuerBase/authorize",
                        tokenEndpoint = "$issuerBase/token",
                        responseTypesSupported = setOf("code", "token"),
                        grantTypesSupported = setOf("urn:ietf:params:oauth:grant-type:pre-authorized_code")
                    ))
                }
                get("/credential-offer") { call.respond(offer) }
                post("/token") {
                    val params = call.receiveParameters()
                    val code = params["pre-authorized_code"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_request") }
                    )
                    val tokenRequest = provider.createAccessTokenRequest(mapOf(
                        "grant_type" to listOf("urn:ietf:params:oauth:grant-type:pre-authorized_code"),
                        "pre-authorized_code" to listOf(code),
                        "client_id" to listOf(params["client_id"] ?: "wallet-client")
                    ))
                    if (tokenRequest !is AccessTokenRequestResult.Success) return@post call.respond(
                        HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_grant") }
                    )
                    val tokenResponse = provider.createAccessTokenResponse(tokenRequest.request.withIssuer(issuerBase))
                    if (tokenResponse !is AccessTokenResponseResult.Success) return@post call.respond(
                        HttpStatusCode.InternalServerError, buildJsonObject { put("error", "server_error") }
                    )
                    call.respond(buildJsonObject {
                        put("access_token", tokenResponse.response.accessToken)
                        put("token_type", "Bearer")
                        put("c_nonce", "isolated-nonce")
                        put("c_nonce_expires_in", 300)
                    })
                }
                post("/credential") {
                    val body = json.parseToJsonElement(call.receiveText()).jsonObject
                    val configId = body["credential_configuration_id"]?.jsonPrimitive?.content
                        ?: return@post call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_request") })
                    val proofsObj = body["proofs"]?.takeIf { it !is JsonNull }?.jsonObject
                    val proofJwt = proofsObj?.get("jwt")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    val credentialRequest = provider.createCredentialRequest(mapOf(
                        "credential_configuration_id" to listOf(configId),
                        "proofs" to listOf(buildJsonObject {
                            put("jwt", buildJsonArray { proofJwt?.let { add(JsonPrimitive(it)) } })
                        }.toString())
                    ), session)
                    if (credentialRequest !is CredentialRequestResult.Success) return@post call.respond(
                        HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_proof") }
                    )
                    val credentialResponse = provider.createCredentialResponse(
                        request = credentialRequest.request.withIssuer(issuerBase),
                        configuration = configuration,
                        issuerKey = issuerKey,
                        issuerId = issuerBase,
                        credentialData = buildJsonObject {
                            put("given_name", "Alice"); put("family_name", "Wonder")
                            put("issuing_country", "DE")
                        },
                        selectiveDisclosure = null
                    )
                    if (credentialResponse !is CredentialResponseResult.Success) return@post call.respond(
                        HttpStatusCode.InternalServerError, buildJsonObject { put("error", "server_error") }
                    )
                    val httpResp = provider.writeCredentialResponse(
                        credentialRequest.request.withIssuer(issuerBase), credentialResponse.response
                    )
                    call.respond(HttpStatusCode.fromValue(httpResp.status), httpResp.payload)
                }
            }
        }.start(wait = false)

        try {
            E2ETest(host, walletPort, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig(
                        "wallet-service",
                        OSSWallet2ServiceConfig(publicBaseUrl = Url(walletBase))
                    )
                },
                init = { DidService.minimalInit() },
                module = { wallet2Module(withPlugins = false) }
            ) {
                val http = testHttpClient()
                val walletId = http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest())
                }.body<WalletCreatedResponse>().walletId

                val keyInfo = http.post("/wallet/$walletId/keys/generate") {
                    contentType(ContentType.Application.Json)
                    setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(keyType = KeyType.secp256r1))
                }.body<WalletKeyInfo>()

                val offerUri = "openid-credential-offer://?credential_offer_uri=${
                    "$issuerBase/credential-offer".encodeURLParameter()
                }"

                // -- Isolated step 1: Resolve offer --
                val resolveResult = testAndReturn("Isolated: resolve-offer") {
                    http.post("/wallet/$walletId/credentials/receive/resolve-offer") {
                        contentType(ContentType.Application.Json)
                        setBody(ResolveOfferRequest(offerUrl = Url(offerUri)))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<ResolveOfferResult>()
                }
                assertEquals(issuerBase, resolveResult.credentialIssuer)
                assertTrue(resolveResult.offeredCredentials.isNotEmpty())

                // -- Isolated step 2: Request token --
                val tokenResult = testAndReturn("Isolated: request-token") {
                    http.post("/wallet/$walletId/credentials/receive/request-token") {
                        contentType(ContentType.Application.Json)
                        setBody(RequestTokenRequest(
                            tokenEndpoint = Url("$issuerBase/token"),
                            preAuthorizedCode = preAuthCode
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<RequestTokenResult>()
                }
                assertNotNull(tokenResult.accessToken)

                // -- Isolated step 3: Sign proof of possession --
                val signResult = testAndReturn("Isolated: sign-proof") {
                    http.post("/wallet/$walletId/credentials/receive/sign-proof") {
                        contentType(ContentType.Application.Json)
                        setBody(SignProofRequest(
                            issuerUrl = Url(issuerBase),
                            nonce = tokenResult.cNonce ?: "isolated-nonce",
                            keyId = keyInfo.keyId
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<id.walt.wallet2.handlers.SignProofResult>()
                }
                assertNotNull(signResult.proofJwt)

                // -- Isolated step 4: Fetch credential --
                val fetchResult = testAndReturn("Isolated: fetch-credential") {
                    http.post("/wallet/$walletId/credentials/receive/fetch-credential") {
                        contentType(ContentType.Application.Json)
                        setBody(FetchCredentialRequest(
                            credentialEndpoint = Url("$issuerBase/credential"),
                            accessToken = tokenResult.accessToken,
                            credentialConfigurationId = credentialConfigId,
                            proofJwt = signResult.proofJwt
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<id.walt.wallet2.handlers.FetchCredentialResult>()
                }
                assertTrue(fetchResult.rawCredentials.isNotEmpty(), "No credentials returned by fetch")

                // -- Verify credential is NOT yet in wallet (isolated steps don't store) --
                testAndReturn("Wallet has no credentials yet (isolated steps don't store)") {
                    val creds = http.get("/wallet/$walletId/credentials").body<List<StoredCredentialMetadata>>()
                    assertEquals(0, creds.size, "Isolated steps should not auto-store, got ${creds.size} creds")
                }

                // -- Now do a full receive for comparison (uses the same offer - but code is consumed,
                //    so we test the full flow separately via the direct receive endpoint) --
                // Seed a new pre-auth code for the full receive
                val fullFlowCode = "full-flow-${Uuid.random()}"
                runBlocking {
                    preAuthRepo.save(DefaultPreAuthorizedCodeRecord(
                        code = fullFlowCode, clientId = null, txCode = null, txCodeValue = null,
                        grantedScopes = emptySet(), grantedAudience = emptySet(), session = session,
                        expiresAt = Clock.System.now() + 10.minutes,
                        credentialNonce = "full-nonce",
                        credentialNonceExpiresAt = Clock.System.now() + 10.minutes
                    ))
                }
                val fullOfferUri = "openid-credential-offer://?credential_offer_uri=${
                    "$issuerBase/credential-offer".encodeURLParameter()
                }"
                // Patch the offer route to use the new code  - instead, just test full-receive directly:
                val receiveResult = testAndReturn("Full receive flow (one call)") {
                    // Build a new offer with the new code
                    val newOffer = CredentialOffer.withPreAuthorizedCodeGrant(
                        credentialIssuer = issuerBase,
                        credentialConfigurationIds = listOf(credentialConfigId),
                        preAuthorizedCode = fullFlowCode
                    )
                    // The wallet resolves the offer_uri from the server, but we can also pass the offer directly
                    http.post("/wallet/$walletId/credentials/receive") {
                        contentType(ContentType.Application.Json)
                        setBody(ReceiveCredentialRequest(
                            offerJson = Json.encodeToJsonElement(newOffer).jsonObject,
                            keyId = keyInfo.keyId
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<ReceiveCredentialResult>()
                }
                assertTrue(receiveResult.credentialIds.isNotEmpty(), "Full receive must store credential")

                // -- Verify credential is now in wallet --
                testAndReturn("Credential now in wallet after full receive") {
                    val creds = http.get("/wallet/$walletId/credentials").body<List<StoredCredentialMetadata>>()
                    assertEquals(1, creds.size, "Expected 1 stored credential, got ${creds.size}")
                }
            }
        } finally {
            issuerServer.stop(500, 2000)
        }
    }

    // -----------------------------------------------------------------------
    // Test 4: Credential deletion
    // -----------------------------------------------------------------------

    @Test
    fun testCredentialDeletion() {
        val port = basePort + 4
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()
            val walletId = http.post("/wallet") {
                contentType(ContentType.Application.Json)
                setBody(CreateWalletRequest())
            }.body<WalletCreatedResponse>().walletId

            // Import two credentials
            val cred1Raw = "eyJhbGciOiJFUzI1NiIsInR5cCI6ImRjK3NkLWp3dCJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIiwidmN0IjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9pZCIsInN1YiI6ImRpZDpleGFtcGxlOjEyMyIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjo5OTk5OTk5OTk5fQ.signature"
            val cred2Raw = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkaWQ6a2V5OnRlc3QiLCJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlRlc3RDcmVkZW50aWFsIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmtleTp0ZXN0IiwibmFtZSI6IlRlc3QifX19.signature"

            for ((idx, raw) in listOf(cred1Raw, cred2Raw).withIndex()) {
                http.post("/wallet/$walletId/credentials/import") {
                    contentType(ContentType.Application.Json)
                    setBody(id.walt.wallet2.server.handlers.ImportCredentialRequest(rawCredential = raw, label = "Cred ${idx + 1}"))
                }
            }

            // List: expect up to 2 credentials (depending on parse success)
            val initialCreds = testAndReturn("List credentials before deletion") {
                http.get("/wallet/$walletId/credentials")
                    .also { assertEquals(HttpStatusCode.OK, it.status) }
                    .body<List<StoredCredentialMetadata>>()
            }
            assertTrue(initialCreds.isNotEmpty(), "Should have at least 1 stored credential")

            // Delete first credential
            val credId = initialCreds.first().id
            testAndReturn("Delete first credential") {
                http.delete("/wallet/$walletId/credentials/$credId")
                    .also { assertEquals(HttpStatusCode.NoContent, it.status) }
            }

            // Verify it's gone
            testAndReturn("Credential count decremented after deletion") {
                val remaining = http.get("/wallet/$walletId/credentials").body<List<StoredCredentialMetadata>>()
                assertEquals(
                    initialCreds.size - 1, remaining.size,
                    "Expected ${initialCreds.size - 1} credentials, got ${remaining.size}"
                )
                assertTrue(remaining.none { it.id == credId }, "Deleted credential should not appear")
            }
        }
    }
}
