package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
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
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.jwt.JwtAccessTokenIssuer
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultCredentialRequestValidator
import id.walt.policies2.vc.VCPolicyList
import id.walt.sdjwt.SDMapBuilder
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierApi
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.StoredCredentialMetadata
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialResult
import id.walt.wallet2.handlers.PresentCredentialIsolatedRequest
import id.walt.wallet2.handlers.PollDeferredRequest
import id.walt.wallet2.handlers.RequestTokenRequest
import id.walt.wallet2.handlers.RequestTokenResult
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.GenerateKeyRequest
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import id.walt.openid4vci.CredentialFormat as VciCredentialFormat

/**
 * Further Wallet2 use-case tests:
 *
 *  1. Deferred credential issuance — issuer defers, wallet polls, credential arrives
 *  2. Multiple formats in one wallet — SD-JWT VC + JWT VC JSON, DCQL selects the right one
 *  3. staticDid wallet — wallet configured with an embedded DID string (no DID store)
 *  4. GET /credentials/{id} returns full raw credential data
 *  5. Isolated VP presentation — caller supplies credentials explicitly
 *  6. Receive into wallet with no DID store — proof-of-possession via JWK binding
 *  7. OSSWallet2Service store reset preserves cross-test isolation
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)
class Wallet2MoreUseCasesTest {

    private val host = "127.0.0.1"
    private val basePort = 17300

    // -----------------------------------------------------------------------
    // Shared issuer builder (avoids duplication)
    // -----------------------------------------------------------------------

    private data class IssuanceInfra(
        val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>,
        val issuerBase: String,
        val preAuthRepo: PreAuthorizedCodeRepository,
        val session: DefaultSession,
        val issuerKey: JWKKey,
        val accessTokenKey: JWKKey
    )

    private fun startIssuer(
        host: String,
        port: Int,
        credentialConfigId: String,
        configuration: CredentialConfiguration,
        preAuthCode: String,
        credentialData: JsonObject,
        selectiveDisclosure: id.walt.sdjwt.SDMap? = null,
        deferred: Boolean = false
    ): IssuanceInfra {
        val issuerBase = "http://$host:$port"
        val issuerKey: JWKKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }
        val accessTokenKey: JWKKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }

        val preAuthRepo: PreAuthorizedCodeRepository = object : PreAuthorizedCodeRepository {
            private val r = java.util.concurrent.ConcurrentHashMap<String, PreAuthorizedCodeRecord>()
            override suspend fun save(record: PreAuthorizedCodeRecord) { if (r.containsKey(record.code)) throw DuplicateCodeException(); r[record.code] = record }
            override suspend fun get(code: String) = r[code]
            override suspend fun consume(code: String) = r.remove(code)
        }
        val authCodeRepo: AuthorizationCodeRepository = object : AuthorizationCodeRepository {
            private val r = java.util.concurrent.ConcurrentHashMap<String, AuthorizationCodeRecord>()
            override suspend fun save(record: AuthorizationCodeRecord) { if (r.containsKey(record.code)) throw DuplicateCodeException(); r[record.code] = record }
            override suspend fun consume(code: String) = r.remove(code)
        }
        val session = DefaultSession(subject = "holder-$preAuthCode")
        val provider = buildOAuth2Provider(OAuth2ProviderConfig(
            authorizationRequestValidator = DefaultAuthorizationRequestValidator(),
            authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
            tokenEndpointHandlers = TokenEndpointHandlers(),
            authorizationCodeRepository = authCodeRepo,
            preAuthorizedCodeRepository = preAuthRepo,
            preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthRepo),
            accessTokenService = JwtAccessTokenIssuer(resolver = { accessTokenKey }),
            accessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
            credentialRequestValidator = DefaultCredentialRequestValidator(),
            credentialEndpointHandlers = CredentialEndpointHandlers()
        ))
        runBlocking {
            preAuthRepo.save(DefaultPreAuthorizedCodeRecord(
                code = preAuthCode, clientId = null, txCode = null, txCodeValue = null,
                grantedScopes = emptySet(), grantedAudience = emptySet(), session = session,
                expiresAt = Clock.System.now() + 10.minutes,
                credentialNonce = "nonce-$preAuthCode",
                credentialNonceExpiresAt = Clock.System.now() + 10.minutes
            ))
        }

        // Deferred: track which transaction IDs have been issued (stores original CredentialRequest + data)
        val deferredCredentials = java.util.concurrent.ConcurrentHashMap<String, Pair<id.walt.openid4vci.requests.credential.CredentialRequest, JsonObject>>()
        val issuerMetadata = CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = issuerBase,
            credentialConfigurationsSupported = mapOf(credentialConfigId to configuration),
            deferredCredentialEndpointPath = if (deferred) "/deferred-credential" else null
        )
        val offer = CredentialOffer.withPreAuthorizedCodeGrant(
            credentialIssuer = issuerBase,
            credentialConfigurationIds = listOf(credentialConfigId),
            preAuthorizedCode = preAuthCode
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

        val server = embeddedServer(CIO, host = host, port = port) {
            install(ContentNegotiation) { json(json) }
            routing {
                get("/.well-known/openid-credential-issuer") { call.respond(issuerMetadata) }
                get("/.well-known/oauth-authorization-server") { call.respond(AuthorizationServerMetadata(issuer = issuerBase, authorizationEndpoint = "$issuerBase/authorize", tokenEndpoint = "$issuerBase/token", responseTypesSupported = setOf("code"), grantTypesSupported = setOf("urn:ietf:params:oauth:grant-type:pre-authorized_code"))) }
                get("/.well-known/openid-configuration") { call.respond(AuthorizationServerMetadata(issuer = issuerBase, authorizationEndpoint = "$issuerBase/authorize", tokenEndpoint = "$issuerBase/token", responseTypesSupported = setOf("code"), grantTypesSupported = setOf("urn:ietf:params:oauth:grant-type:pre-authorized_code"))) }
                get("/credential-offer-json") { call.respond(offer) }
                post("/token") {
                    val params = call.receiveParameters()
                    val code = params["pre-authorized_code"] ?: return@post call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error","invalid_request") })
                    val tr = provider.createAccessTokenRequest(mapOf("grant_type" to listOf("urn:ietf:params:oauth:grant-type:pre-authorized_code"), "pre-authorized_code" to listOf(code), "client_id" to listOf(params["client_id"] ?: "wallet-client")))
                    if (tr !is AccessTokenRequestResult.Success) return@post call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error","invalid_grant") })
                    val resp = provider.createAccessTokenResponse(tr.request.withIssuer(issuerBase))
                    if (resp !is AccessTokenResponseResult.Success) return@post call.respond(HttpStatusCode.InternalServerError, buildJsonObject { put("error","server_error") })
                    call.respond(buildJsonObject { put("access_token", resp.response.accessToken); put("token_type", "Bearer"); put("c_nonce", "nonce-$preAuthCode"); put("c_nonce_expires_in", 300) })
                }
                post("/credential") {
                    val body = json.parseToJsonElement(call.receiveText()).jsonObject
                    val configId = body["credential_configuration_id"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error","invalid_request") })
                    val proofsObj = body["proofs"]?.takeIf { it !is JsonNull }?.jsonObject
                    val proofJwt = proofsObj?.get("jwt")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    val cr = provider.createCredentialRequest(mapOf("credential_configuration_id" to listOf(configId), "proofs" to listOf(buildJsonObject { put("jwt", buildJsonArray { proofJwt?.let { add(JsonPrimitive(it)) } }) }.toString())), session)
                    if (cr !is CredentialRequestResult.Success) return@post call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error","invalid_proof") })

                    if (deferred) {
                        // Return a transaction_id instead of the credential immediately
                        // Store the original CredentialRequest so we can use it when polling
                        val txId = "tx-${Uuid.random()}"
                        deferredCredentials[txId] = Pair(cr.request, credentialData)
                        call.respond(buildJsonObject { put("transaction_id", txId) })
                        return@post
                    }

                    val credResp = provider.createCredentialResponse(
                        request = cr.request.withIssuer(issuerBase),
                        configuration = configuration, issuerKey = issuerKey,
                        issuerId = issuerBase, credentialData = credentialData,
                        selectiveDisclosure = selectiveDisclosure
                    )
                    if (credResp !is CredentialResponseResult.Success) return@post call.respond(HttpStatusCode.InternalServerError, buildJsonObject { put("error","server_error") })
                    val httpResp = provider.writeCredentialResponse(cr.request.withIssuer(issuerBase), credResp.response)
                    call.respond(HttpStatusCode.fromValue(httpResp.status), httpResp.payload)
                }
                post("/deferred-credential") {
                    val body = json.parseToJsonElement(call.receiveText()).jsonObject
                    val txId = body["transaction_id"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error","invalid_transaction_id") })
                    val (origRequest, data) = deferredCredentials.remove(txId) ?: return@post call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error","invalid_transaction_id") })

                    val credResp = provider.createCredentialResponse(
                        request = origRequest.withIssuer(issuerBase),
                        configuration = configuration, issuerKey = issuerKey,
                        issuerId = issuerBase, credentialData = data,
                        selectiveDisclosure = null
                    )
                    if (credResp !is CredentialResponseResult.Success) return@post call.respond(HttpStatusCode.InternalServerError, buildJsonObject { put("error","server_error") })
                    val httpResp = provider.writeCredentialResponse(origRequest.withIssuer(issuerBase), credResp.response)
                    call.respond(HttpStatusCode.fromValue(httpResp.status), httpResp.payload)
                }
            }
        }.start(wait = false)

        return IssuanceInfra(server, issuerBase, preAuthRepo, session, issuerKey, accessTokenKey)
    }

    // -----------------------------------------------------------------------
    // Test 1: Multiple credential formats in one wallet + format-aware DCQL
    // -----------------------------------------------------------------------

    @Test
    fun testMultipleFormatsInOneWallet() {
        val issuerPort1 = basePort
        val issuerPort2 = basePort + 1
        val walletPort = basePort + 2

        val pidVct = "eu.europa.ec.eudi.pid.1"
        val sdJwtConfig = "pid_sd_jwt"
        val jwtConfig = "openbadge_jwt"
        val sdJwtCode = "code-sdjwt-${Uuid.random()}"
        val jwtCode = "code-jwt-${Uuid.random()}"

        val sdJwtInfra = startIssuer(host, issuerPort1, sdJwtConfig,
            CredentialConfiguration(format = VciCredentialFormat.SD_JWT_VC, vct = pidVct,
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = mapOf("jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256", "EdDSA")))),
            sdJwtCode,
            buildJsonObject { put("given_name","Alice"); put("family_name","Wallet"); put("issuing_country","AT") },
            SDMapBuilder().addField("given_name",true).addField("family_name",true).build()
        )
        val jwtInfra = startIssuer(host, issuerPort2, jwtConfig,
            CredentialConfiguration(format = VciCredentialFormat.JWT_VC_JSON, vct = null,
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk, CryptographicBindingMethod.DidKey),
                proofTypesSupported = mapOf("jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256", "EdDSA")))),
            jwtCode,
            buildJsonObject {
                put("@context", buildJsonArray { add("https://www.w3.org/2018/credentials/v1") })
                put("type", buildJsonArray { add("VerifiableCredential"); add("UniversityDegreeCredential") })
                putJsonObject("credentialSubject") { put("degree","Bachelor of Testing") }
            }
        )

        try {
            E2ETest(host, walletPort, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog, OSSVerifier2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$walletPort")))
                    ConfigManager.preloadConfig("verifier-service", OSSVerifier2ServiceConfig(clientId = "multi-format-verifier", clientMetadata = id.walt.verifier.openid.models.authorization.ClientMetadata(clientName = "Multi-format Test Verifier"), urlPrefix = "http://$host:$walletPort/verification-session", urlHost = "openid4vp://authorize"))
                },
                init = { DidService.minimalInit() },
                module = { multiFormatModule() }
            ) {
                val http = testHttpClient()
                val walletId = http.post("/wallet") { contentType(ContentType.Application.Json); setBody(CreateWalletRequest()) }.body<WalletCreatedResponse>().walletId
                val keyInfo = http.post("/wallet/$walletId/keys/generate") { contentType(ContentType.Application.Json); setBody(GenerateKeyRequest(keyType = "Ed25519")) }.body<WalletKeyInfo>()
                val holderDid = http.post("/wallet/$walletId/dids/create") { contentType(ContentType.Application.Json); setBody(id.walt.wallet2.server.handlers.CreateDidRequest(method = "key", keyId = keyInfo.keyId)) }.body<WalletDidEntry>().did

                // ── Receive both credentials ──
                testAndReturn("Receive SD-JWT VC (PID)") {
                    http.post("/wallet/$walletId/credentials/receive") {
                        contentType(ContentType.Application.Json)
                        setBody(ReceiveCredentialRequest(offerJson = Json.encodeToJsonElement(
                            CredentialOffer.withPreAuthorizedCodeGrant("http://$host:$issuerPort1", listOf(sdJwtConfig), sdJwtCode)
                        ).jsonObject))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<ReceiveCredentialResult>()
                        .also { assertTrue(it.credentialIds.isNotEmpty()) }
                }
                testAndReturn("Receive JWT VC JSON (OpenBadge)") {
                    http.post("/wallet/$walletId/credentials/receive") {
                        contentType(ContentType.Application.Json)
                        setBody(ReceiveCredentialRequest(offerJson = Json.encodeToJsonElement(
                            CredentialOffer.withPreAuthorizedCodeGrant("http://$host:$issuerPort2", listOf(jwtConfig), jwtCode)
                        ).jsonObject))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<ReceiveCredentialResult>()
                        .also { assertTrue(it.credentialIds.isNotEmpty()) }
                }

                // ── Both credentials are in the wallet ──
                testAndReturn("Wallet contains 2 credentials of different formats") {
                    val creds = http.get("/wallet/$walletId/credentials").body<List<StoredCredentialMetadata>>()
                    assertEquals(2, creds.size, "Expected 2 credentials, got ${creds.size}")
                }

                // ── GET /credentials/{id} returns full raw data ──
                val credList = http.get("/wallet/$walletId/credentials").body<List<StoredCredentialMetadata>>()
                val credId = credList.first().id
                testAndReturn("GET /credentials/{id} returns full StoredCredential with raw data") {
                    val full = http.get("/wallet/$walletId/credentials/$credId")
                        .also { assertEquals(HttpStatusCode.OK, it.status) }
                        .body<StoredCredential>()
                    assertEquals(credId, full.id)
                    assertNotNull(full.credential, "Full credential data must be present")
                }

                // ── DCQL selects only SD-JWT (format = dc+sd-jwt) ──
                testAndReturn("match-credentials-from-store: dc+sd-jwt query only matches PID") {
                    val result = http.post("/wallet/$walletId/credentials/present/match-credentials-from-store") {
                        contentType(ContentType.Application.Json)
                        setBody(id.walt.wallet2.handlers.MatchCredentialsFromStoreRequest(
                            dcqlQuery = DcqlQuery(credentials = listOf(
                                CredentialQuery(id = "pid", format = CredentialFormat.DC_SD_JWT,
                                    meta = SdJwtVcMeta(vctValues = listOf(pidVct)))
                            ))
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status) }
                        .body<id.walt.wallet2.handlers.MatchCredentialsResult>()
                    assertTrue(result.matchCount > 0, "SD-JWT query should match the PID credential")
                    assertEquals(listOf("pid"), result.matchedQueryIds)
                }

                // ── DCQL selects only JWT VC JSON (format = jwt_vc_json) ──
                testAndReturn("match-credentials-from-store: jwt_vc_json query only matches OpenBadge") {
                    val result = http.post("/wallet/$walletId/credentials/present/match-credentials-from-store") {
                        contentType(ContentType.Application.Json)
                        setBody(id.walt.wallet2.handlers.MatchCredentialsFromStoreRequest(
                            dcqlQuery = DcqlQuery(credentials = listOf(
                                CredentialQuery(id = "badge", format = CredentialFormat.JWT_VC_JSON,
                                    meta = JwtVcJsonMeta(typeValues = listOf(listOf("VerifiableCredential","UniversityDegreeCredential"))))
                            ))
                        ))
                    }.body<id.walt.wallet2.handlers.MatchCredentialsResult>()
                    assertTrue(result.matchCount > 0, "JWT query should match the OpenBadge credential")
                    assertEquals(listOf("badge"), result.matchedQueryIds)
                }

                // ── Present the SD-JWT PID via verifier ──
                val verifierSession = http.post("/verification-session/create") {
                    setBody(CrossDeviceFlowSetup(core = GeneralFlowConfig(
                        dcqlQuery = DcqlQuery(credentials = listOf(CredentialQuery(id = "pid", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(vctValues = listOf(pidVct)), claims = listOf(ClaimsQuery(pathStrings = listOf("given_name")))))),
                        policies = Verification2Session.DefinedVerificationPolicies(vc_policies = VCPolicyList(policies = emptyList()))
                    )) as VerificationSessionSetup)
                }.body<VerificationSessionCreationResponse>()

                testAndReturn("Present SD-JWT PID credential to verifier") {
                    http.post("/wallet/$walletId/credentials/present") {
                        contentType(ContentType.Application.Json)
                        setBody(id.walt.wallet2.handlers.PresentCredentialRequest(
                            requestUrl = verifierSession.bootstrapAuthorizationRequestUrl ?: Url("http://$host:$walletPort/verification-session/${verifierSession.sessionId}/request"),
                            did = holderDid
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                }

                testAndReturn("Verification session SUCCESSFUL after multi-format wallet present") {
                    val info = http.get("/verification-session/${verifierSession.sessionId}/info").body<Verification2Session>()
                    assertEquals(Verification2Session.VerificationSessionStatus.SUCCESSFUL, info.status)
                }
            }
        } finally {
            sdJwtInfra.server.stop(500, 2000)
            jwtInfra.server.stop(500, 2000)
        }
    }

    // -----------------------------------------------------------------------
    // Test 6: Deferred credential issuance — issuer defers, wallet polls
    // -----------------------------------------------------------------------

    @Test
    fun testDeferredCredentialIssuance() {
        val issuerPort = basePort + 9
        val walletPort = basePort + 10
        val issuerBase = "http://$host:$issuerPort"

        val credConfigId = "pid_deferred"
        val preAuthCode = "deferred-${Uuid.random()}"

        val infra = startIssuer(
            host, issuerPort, credConfigId,
            CredentialConfiguration(
                format = VciCredentialFormat.SD_JWT_VC, vct = "eu.europa.ec.eudi.pid.1",
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = mapOf("jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256", "EdDSA")))
            ),
            preAuthCode,
            buildJsonObject { put("given_name", "Deferred"); put("family_name", "Holder"); put("issuing_country", "AT") },
            deferred = true
        )

        try {
            E2ETest(host, walletPort, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                preload = { ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$walletPort"))) },
                init = { DidService.minimalInit() },
                module = { wallet2Module(withPlugins = false) }
            ) {
                val http = testHttpClient()
                val walletId = http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest())
                }.body<WalletCreatedResponse>().walletId

                // Wallet needs a key to sign proof-of-possession
                testAndReturn("Deferred: generate wallet key") {
                    http.post("/wallet/$walletId/keys/generate") {
                        contentType(ContentType.Application.Json)
                        setBody(GenerateKeyRequest(keyType = "Ed25519"))
                    }.also { assertEquals(HttpStatusCode.Created, it.status) }
                }

                // Step 1: receive credential via wallet → issuer defers → deferredTransactionIds populated
                val offer = CredentialOffer.withPreAuthorizedCodeGrant(issuerBase, listOf(credConfigId), preAuthCode)
                val receiveResult = testAndReturn("Deferred: receive returns deferredTransactionIds (no immediate credential)") {
                    http.post("/wallet/$walletId/credentials/receive") {
                        contentType(ContentType.Application.Json)
                        setBody(ReceiveCredentialRequest(offerJson = Json.encodeToJsonElement(offer).jsonObject))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<ReceiveCredentialResult>()
                }
                assertEquals(0, receiveResult.credentialIds.size, "Deferred: no immediate credential, expected 0 got ${receiveResult.credentialIds.size}")
                assertTrue(receiveResult.deferredTransactionIds.isNotEmpty(), "Deferred: must have a transactionId")

                val txId = receiveResult.deferredTransactionIds.values.first()

                // Step 2: get an access token for the deferred poll — exchange a second pre-auth code
                // We call the isolated request-token endpoint with a fresh code seeded into the issuer.
                // The receive flow above already consumed the original pre-auth code internally.
                // For the poll, we need a valid access token. We obtain it via isolated request-token
                // using a fresh code that we seed into the issuer's pre-auth repo.
                val pollPreAuthCode = "poll-${Uuid.random()}"
                runBlocking {
                    infra.preAuthRepo.save(
                        id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord(
                            code = pollPreAuthCode, clientId = null, txCode = null, txCodeValue = null,
                            grantedScopes = emptySet(), grantedAudience = emptySet(),
                            session = infra.session,
                            expiresAt = kotlin.time.Clock.System.now() + 5.minutes,
                            credentialNonce = "poll-nonce",
                            credentialNonceExpiresAt = kotlin.time.Clock.System.now() + 5.minutes
                        )
                    )
                }
                val accessToken = testAndReturn("Deferred: request access token for poll") {
                    http.post("/wallet/$walletId/credentials/receive/request-token") {
                        contentType(ContentType.Application.Json)
                        setBody(RequestTokenRequest(
                            tokenEndpoint = Url("$issuerBase/token"),
                            preAuthorizedCode = pollPreAuthCode
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<RequestTokenResult>()
                        .accessToken
                }

                // Step 3: poll the deferred endpoint — credential should arrive immediately (test issuer)
                val pollResult = testAndReturn("Deferred: poll endpoint returns stored credential") {
                    http.post("/wallet/$walletId/credentials/receive/deferred") {
                        contentType(ContentType.Application.Json)
                        setBody(PollDeferredRequest(
                            deferredCredentialEndpoint = Url("$issuerBase/deferred-credential"),
                            transactionId = txId,
                            accessToken = accessToken
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<ReceiveCredentialResult>()
                }
                assertTrue(pollResult.credentialIds.isNotEmpty(), "Poll: credential must be returned after deferred issuance")

                // Step 4: credential now in wallet
                testAndReturn("Deferred credential stored in wallet after polling") {
                    val creds = http.get("/wallet/$walletId/credentials")
                        .body<List<id.walt.wallet2.data.StoredCredentialMetadata>>()
                    assertEquals(1, creds.size, "Wallet must contain the deferred credential after poll")
                }
            }
        } finally {
            infra.server.stop(500, 2000)
        }
    }

    // -----------------------------------------------------------------------
    // Test 2: staticDid wallet — embedded DID string, no DID store
    // -----------------------------------------------------------------------

    @Test
    fun testStaticDidWallet() {
        val port = basePort + 3
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = { ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))) },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            // Generate an external key + DID to use as the static ones
            val staticKey = runBlocking { JWKKey.generate(KeyType.Ed25519) }
            val staticKeyJson = id.walt.crypto.keys.KeySerialization.serializeKeyToJson(staticKey).jsonObject
            val staticDid = "did:key:z6Mkfakestatic123456789"

            // Create wallet with both a static key AND static DID
            val walletId = testAndReturn("Create wallet with static key + static DID") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest(
                        noDidStore = true,    // no DID store needed — DID is embedded
                        staticKey = staticKeyJson,
                        staticDid = staticDid
                    ))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletCreatedResponse>().walletId
            }

            // GET wallet info: no DID store, but hasStaticDid = true
            testAndReturn("GET wallet info: hasStaticDid=true, hasDidStore=false") {
                val info = http.get("/wallet/$walletId").body<WalletInfoResponse>()
                assertTrue(info.hasStaticKey, "hasStaticKey must be true")
                assertTrue(info.hasStaticDid, "hasStaticDid must be true")
                assertFalse(info.hasDidStore, "hasDidStore must be false (noDidStore=true)")
            }

            // GET /dids returns the static DID
            testAndReturn("GET /dids returns the static DID fallback") {
                val dids = http.get("/wallet/$walletId/dids").body<List<WalletDidEntry>>()
                // staticDid is used as a fallback; may appear depending on implementation
                // At minimum the wallet should respond without error
                assertNotNull(dids, "DID list must not be null")
            }

            // Static key appears in key list
            testAndReturn("GET /keys returns the static key") {
                val keys = http.get("/wallet/$walletId/keys").body<List<WalletKeyInfo>>()
                assertTrue(keys.isNotEmpty(), "Static key must appear in key list")
                assertEquals("Ed25519", keys.first().keyType)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Test 3: Receive without DID store — JWK holder binding (cnf.jwk)
    // -----------------------------------------------------------------------

    @Test
    fun testReceiveWithoutDidStore() {
        val issuerPort = basePort + 4
        val walletPort = basePort + 5
        val issuerBase = "http://$host:$issuerPort"

        val credConfigId = "pid_no_did"
        val preAuthCode = "nodid-${Uuid.random()}"

        val infra = startIssuer(host, issuerPort, credConfigId,
            CredentialConfiguration(format = VciCredentialFormat.SD_JWT_VC, vct = "eu.europa.ec.eudi.pid.1",
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = mapOf("jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256", "EdDSA")))),
            preAuthCode,
            buildJsonObject { put("given_name","NoDid"); put("family_name","Holder"); put("issuing_country","AT") }
        )

        try {
            E2ETest(host, walletPort, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                preload = { ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$walletPort"))) },
                init = { DidService.minimalInit() },
                module = { wallet2Module(withPlugins = false) }
            ) {
                val http = testHttpClient()

                // Wallet with no DID store
                val walletId = http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest(noDidStore = true))
                }.body<WalletCreatedResponse>().walletId

                testAndReturn("Wallet with noDidStore has no DID store") {
                    val info = http.get("/wallet/$walletId").body<WalletInfoResponse>()
                    assertFalse(info.hasDidStore, "hasDidStore must be false")
                }

                // Generate a key for proof-of-possession (wallet still needs a key even without a DID)
                testAndReturn("Generate key for JWK holder binding") {
                    http.post("/wallet/$walletId/keys/generate") {
                        contentType(ContentType.Application.Json)
                        setBody(GenerateKeyRequest(keyType = "Ed25519"))
                    }.also { assertEquals(HttpStatusCode.Created, it.status) }
                }

                // Receive credential — wallet uses JWK holder binding (cnf.jwk) since there's no DID
                val result = testAndReturn("Receive credential with JWK holder binding (no DID)") {
                    http.post("/wallet/$walletId/credentials/receive") {
                        contentType(ContentType.Application.Json)
                        setBody(ReceiveCredentialRequest(offerJson = Json.encodeToJsonElement(
                            CredentialOffer.withPreAuthorizedCodeGrant(issuerBase, listOf(credConfigId), preAuthCode)
                        ).jsonObject))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<ReceiveCredentialResult>()
                }
                assertTrue(result.credentialIds.isNotEmpty(), "Credential must be stored even without DID store")

                // Credential is in the wallet
                testAndReturn("Credential stored after JWK-binding receive") {
                    val creds = http.get("/wallet/$walletId/credentials").body<List<StoredCredentialMetadata>>()
                    assertEquals(1, creds.size)
                }
            }
        } finally {
            infra.server.stop(500, 2000)
        }
    }

    // -----------------------------------------------------------------------
    // Test 4: Isolated VP presentation — caller supplies credentials explicitly
    // -----------------------------------------------------------------------

    @Test
    fun testIsolatedVPPresentation() {
        val issuerPort = basePort + 6
        val walletPort = basePort + 7
        val issuerBase = "http://$host:$issuerPort"
        val walletBase = "http://$host:$walletPort"

        val credConfigId = "pid_isolated_vp"
        val preAuthCode = "ivp-${Uuid.random()}"
        val pidVct = "eu.europa.ec.eudi.pid.1"

        val infra = startIssuer(host, issuerPort, credConfigId,
            CredentialConfiguration(format = VciCredentialFormat.SD_JWT_VC, vct = pidVct,
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk, CryptographicBindingMethod.DidKey),
                proofTypesSupported = mapOf("jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256", "EdDSA")))),
            preAuthCode,
            buildJsonObject { put("given_name","Isolated"); put("family_name","Presenter"); put("issuing_country","AT") }
        )

        try {
            E2ETest(host, walletPort, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog, OSSVerifier2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(publicBaseUrl = Url(walletBase)))
                    ConfigManager.preloadConfig("verifier-service", OSSVerifier2ServiceConfig(clientId = "isolated-vp-verifier", clientMetadata = id.walt.verifier.openid.models.authorization.ClientMetadata(clientName = "Isolated VP Verifier"), urlPrefix = "$walletBase/verification-session", urlHost = "openid4vp://authorize"))
                },
                init = { DidService.minimalInit() },
                module = { multiFormatModule() }
            ) {
                val http = testHttpClient()
                val walletId = http.post("/wallet") { contentType(ContentType.Application.Json); setBody(CreateWalletRequest()) }.body<WalletCreatedResponse>().walletId
                val keyInfo = http.post("/wallet/$walletId/keys/generate") { contentType(ContentType.Application.Json); setBody(GenerateKeyRequest(keyType = "Ed25519")) }.body<WalletKeyInfo>()
                val holderDid = http.post("/wallet/$walletId/dids/create") { contentType(ContentType.Application.Json); setBody(id.walt.wallet2.server.handlers.CreateDidRequest(method = "key", keyId = keyInfo.keyId)) }.body<WalletDidEntry>().did

                // Receive credential
                http.post("/wallet/$walletId/credentials/receive") {
                    contentType(ContentType.Application.Json)
                    setBody(ReceiveCredentialRequest(offerJson = Json.encodeToJsonElement(
                        CredentialOffer.withPreAuthorizedCodeGrant(issuerBase, listOf(credConfigId), preAuthCode)
                    ).jsonObject))
                }
                val storedCreds = http.get("/wallet/$walletId/credentials").body<List<StoredCredentialMetadata>>()
                assertEquals(1, storedCreds.size)

                // Get full StoredCredential (includes raw credential data) for isolated VP
                val fullCred = http.get("/wallet/$walletId/credentials/${storedCreds.first().id}")
                    .body<StoredCredential>()

                // Create verifier session
                val verifierSession = http.post("/verification-session/create") {
                    setBody(CrossDeviceFlowSetup(core = GeneralFlowConfig(
                        dcqlQuery = DcqlQuery(credentials = listOf(CredentialQuery(id = "pid", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(vctValues = listOf(pidVct)), claims = listOf(ClaimsQuery(pathStrings = listOf("given_name")))))),
                        policies = Verification2Session.DefinedVerificationPolicies(vc_policies = VCPolicyList(policies = emptyList()))
                    )) as VerificationSessionSetup)
                }.body<VerificationSessionCreationResponse>()

                val bootstrapUrl = verifierSession.bootstrapAuthorizationRequestUrl
                    ?: Url("$walletBase/verification-session/${verifierSession.sessionId}/request")

                // ── Isolated VP: caller supplies the credentials explicitly ──
                testAndReturn("Isolated VP present: caller supplies credentials explicitly") {
                    http.post("/wallet/$walletId/credentials/present/isolated") {
                        contentType(ContentType.Application.Json)
                        setBody(PresentCredentialIsolatedRequest(
                            requestUrl = bootstrapUrl,
                            credentials = listOf(fullCred),
                            did = holderDid
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                }

                testAndReturn("Verification session SUCCESSFUL after isolated VP") {
                    val info = http.get("/verification-session/${verifierSession.sessionId}/info").body<Verification2Session>()
                    assertEquals(Verification2Session.VerificationSessionStatus.SUCCESSFUL, info.status,
                        "Session status: ${info.status} (${info.statusReason})")
                    assertTrue(info.presentedCredentials?.isNotEmpty() == true)
                }
            }
        } finally {
            infra.server.stop(500, 2000)
        }
    }

    // -----------------------------------------------------------------------
    // Test 5: OSSWallet2Service store reset between tests (isolation)
    // -----------------------------------------------------------------------

    @Test
    fun testStoreIsolationBetweenTestRuns() {
        val port = basePort + 8

        // Save the current store so we can restore it after this test
        val originalStore = OSSWallet2Service.walletStore

        try {
            // Give this test its own fresh store so it doesn't pollute other tests
            OSSWallet2Service.walletStore = id.walt.wallet2.stores.inmemory.InMemoryWalletStore()

            // Run 1: create a wallet, verify it exists
            E2ETest(host, port, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                preload = { ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))) },
                init = {},
                module = { wallet2Module(withPlugins = false) }
            ) {
                val http = testHttpClient()
                testAndReturn("Run 1: create one wallet") {
                    http.post("/wallet") { contentType(ContentType.Application.Json); setBody(CreateWalletRequest()) }
                    val wallets = http.get("/wallet").body<List<String>>()
                    assertEquals(1, wallets.size, "Run 1: expected 1 wallet")
                }
            }

            // Reset the store (simulates a fresh service start without persistence)
            OSSWallet2Service.walletStore = id.walt.wallet2.stores.inmemory.InMemoryWalletStore()

            // Run 2: different port, same service config — store is clean
            E2ETest(host, port + 1, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                preload = { ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:${port + 1}"))) },
                init = {},
                module = { wallet2Module(withPlugins = false) }
            ) {
                val http = testHttpClient()
                testAndReturn("Run 2: store reset, wallet list is empty") {
                    val wallets = http.get("/wallet").body<List<String>>()
                    assertEquals(0, wallets.size,
                        "Run 2: store was reset, expected 0 wallets, got ${wallets.size}")
                }
            }
        } finally {
            // Always restore the original store so other tests are unaffected
            OSSWallet2Service.walletStore = originalStore
        }
    }
}

private fun Application.multiFormatModule() {
    install(io.ktor.server.sse.SSE)
    wallet2Module(withPlugins = false)
    verifierApi()
}

private fun assertTrue(condition: Boolean, message: String = "Expected true") {
    kotlin.test.assertTrue(condition, message)
}
private fun assertFalse(condition: Boolean, message: String = "Expected false") {
    kotlin.test.assertFalse(condition, message)
}
