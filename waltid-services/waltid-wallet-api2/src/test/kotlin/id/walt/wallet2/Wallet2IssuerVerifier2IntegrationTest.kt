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
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.DefinedVerificationPolicies
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierApi
import id.walt.wallet2.data.StoredCredentialMetadata
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.handlers.PresentCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialResult
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import id.walt.openid4vci.CredentialFormat as VciCredentialFormat

/**
 * End-to-end integration tests: OpenID4VCI 1.0 in-process issuer → Wallet2 → Verifier2 (OpenID4VP 1.0).
 *
 * The issuer is a minimal Ktor server backed by [buildOAuth2Provider] from the
 * waltid-openid4vci library (OpenID4VCI 1.0 compliant). It runs on [issuerPort]
 * while wallet2 + verifier2 share [walletPort].
 *
 * Covered:
 *   1. EU PID as dc+sd-jwt  (vct = eu.europa.ec.eudi.pid.1) — with selective disclosure
 *   2. OpenBadge as jwt_vc_json — without selective disclosure
 *
 * Out of scope: mso_mdoc — the waltid-openid4vci library has no mdoc credential handler.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)
class Wallet2IssuerVerifier2IntegrationTest {

    // Each test gets its own pair of ports to avoid Address-already-in-use when
    // the previous test's embedded server hasn't fully stopped yet.
    companion object {
        private val nextPortBase = java.util.concurrent.atomic.AtomicInteger(17060)
    }

    // Issuer signing key (ES256) — generated once at class load time
    private val issuerKey: JWKKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }
    private val accessTokenKey: JWKKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }

    // Tell the wallet to build a JWT proof for ES256 and Ed25519 keys
    private val jwtProofTypesSupported = mapOf(
        "jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256", "EdDSA"))
    )

    // -----------------------------------------------------------------------
    // Test 1 — EU PID as dc+sd-jwt
    // -----------------------------------------------------------------------

    @Test
    fun `eu pid sd-jwt vc full flow`() {
        val credentialConfigId = "pid_sd_jwt"
        val pidVct = "eu.europa.ec.eudi.pid.1"

        runE2EFlow(
            tag = "eu-pid",
            credentialConfigId = credentialConfigId,
            configuration = CredentialConfiguration(
                format = VciCredentialFormat.SD_JWT_VC,
                vct = pidVct,
                cryptographicBindingMethodsSupported = setOf(
                    CryptographicBindingMethod.Jwk,
                    CryptographicBindingMethod.DidKey
                ),
                proofTypesSupported = jwtProofTypesSupported
            ),
            credentialData = buildJsonObject {
                put("given_name", "Alice")
                put("family_name", "Wonderland")
                put("birth_date", "1990-01-15")
                put("age_over_18", true)
                put("issuing_country", "DE")
                put("issuing_authority", "Bundesdruckerei GmbH")
            },
            selectiveDisclosure = SDMapBuilder()
                .addField("given_name", true)
                .addField("family_name", true)
                .addField("birth_date", true)
                .addField("age_over_18", true)
                .addField("issuing_country", false)
                .addField("issuing_authority", false)
                .build(),
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "pid",
                        format = CredentialFormat.DC_SD_JWT,
                        meta = SdJwtVcMeta(vctValues = listOf(pidVct)),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("given_name")),
                            ClaimsQuery(pathStrings = listOf("family_name")),
                            ClaimsQuery(pathStrings = listOf("age_over_18"))
                        )
                    )
                )
            )
        )
    }

    // -----------------------------------------------------------------------
    // Test 2 — OpenBadge as jwt_vc_json
    // -----------------------------------------------------------------------

    @Test
    fun `openbadge jwt vc json full flow`() {
        val credentialConfigId = "OpenBadgeCredential_jwt_vc_json"

        runE2EFlow(
            tag = "openbadge",
            credentialConfigId = credentialConfigId,
            configuration = CredentialConfiguration(
                format = VciCredentialFormat.JWT_VC_JSON,
                vct = null,
                cryptographicBindingMethodsSupported = setOf(
                    CryptographicBindingMethod.Jwk,
                    CryptographicBindingMethod.DidKey
                ),
                proofTypesSupported = jwtProofTypesSupported
            ),
            credentialData = buildJsonObject {
                put("@context", buildJsonArray {
                    add(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))
                    add(JsonPrimitive("https://purl.imsglobal.org/spec/ob/v3p0/context-3.0.2.json"))
                })
                put("type", buildJsonArray {
                    add(JsonPrimitive("VerifiableCredential"))
                    add(JsonPrimitive("OpenBadgeCredential"))
                })
                putJsonObject("credentialSubject") {
                    putJsonObject("achievement") {
                        put("name", "Test Achievement")
                        put("type", "Achievement")
                    }
                }
            },
            selectiveDisclosure = null,
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "badge",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("credentialSubject", "achievement", "name"))
                        )
                    )
                )
            )
        )
    }

    // -----------------------------------------------------------------------
    // Core E2E runner
    // -----------------------------------------------------------------------

    private fun runE2EFlow(
        tag: String,
        credentialConfigId: String,
        configuration: CredentialConfiguration,
        credentialData: JsonObject,
        selectiveDisclosure: id.walt.sdjwt.SDMap?,
        dcqlQuery: DcqlQuery
    ) {
        // Allocate a fresh port pair for this test run to avoid BindException
        val issuerPort = nextPortBase.getAndAdd(2)
        val walletPort = issuerPort + 1
        val issuerBase = "http://127.0.0.1:$issuerPort"
        val walletBase = "http://127.0.0.1:$walletPort"

        val preAuthCode = "pre-auth-${Uuid.random()}"

        // Build the OID4VCI 1.0 provider with inline in-memory repositories
        // (the library's DefaultPreAuthorizedCodeRepository and DefaultAuthorizationCodeRepository
        //  are internal; we provide equivalent anonymous implementations here)
        val preAuthRepo: PreAuthorizedCodeRepository = object : PreAuthorizedCodeRepository {
            private val records = java.util.concurrent.ConcurrentHashMap<String, PreAuthorizedCodeRecord>()
            override suspend fun save(record: PreAuthorizedCodeRecord) {
                if (records.containsKey(record.code)) throw DuplicateCodeException()
                records[record.code] = record
            }
            override suspend fun get(code: String) = records[code]
            override suspend fun consume(code: String) = records.remove(code)
        }
        val authCodeRepo: AuthorizationCodeRepository = object : AuthorizationCodeRepository {
            private val records = java.util.concurrent.ConcurrentHashMap<String, AuthorizationCodeRecord>()
            override suspend fun save(record: AuthorizationCodeRecord) {
                if (records.containsKey(record.code)) throw DuplicateCodeException()
                records[record.code] = record
            }
            override suspend fun consume(code: String) = records.remove(code)
        }
        val provider = buildOAuth2Provider(
            OAuth2ProviderConfig(
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
            )
        )

        // Seed the pre-authorized code directly into the repository
        val session = DefaultSession(subject = "holder-${Uuid.random()}")
        runBlocking {
            preAuthRepo.save(
                DefaultPreAuthorizedCodeRecord(
                    code = preAuthCode,
                    clientId = null,
                    txCode = null,
                    txCodeValue = null,
                    grantedScopes = emptySet(),
                    grantedAudience = emptySet(),
                    session = session,
                    expiresAt = Clock.System.now() + 10.minutes,
                    credentialNonce = "test-nonce",
                    credentialNonceExpiresAt = Clock.System.now() + 10.minutes
                )
            )
        }

        val issuerMetadata = CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = issuerBase,
            credentialConfigurationsSupported = mapOf(credentialConfigId to configuration)
            // authorizationServers deliberately NOT set — IssuerMetadataResolver has a bug
            // when authorizationServers is set and resolution fails (Unit cast to AS metadata).
            // Instead we let the fallback path run which calls resolveAuthorizationServerMetadata
            // directly on the credentialIssuer URL, hitting our /.well-known/oauth-authorization-server.
        )

        val offer = CredentialOffer.withPreAuthorizedCodeGrant(
            credentialIssuer = issuerBase,
            credentialConfigurationIds = listOf(credentialConfigId),
            preAuthorizedCode = preAuthCode
        )

        // Start the in-process issuer
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        val issuerServer = embeddedServer(CIO, host = "127.0.0.1", port = issuerPort) {
            install(ContentNegotiation) { json(json) }
            routing {
                get("/.well-known/openid-credential-issuer") {
                    call.respond(issuerMetadata)
                }
                get("/.well-known/oauth-authorization-server") {
                    call.respond(
                        AuthorizationServerMetadata(
                            issuer = issuerBase,
                            authorizationEndpoint = "$issuerBase/authorize",
                            tokenEndpoint = "$issuerBase/token",
                            responseTypesSupported = setOf("code", "token"),
                            grantTypesSupported = setOf("urn:ietf:params:oauth:grant-type:pre-authorized_code")
                        )
                    )
                }
                // Also serve openid-configuration for fallback
                get("/.well-known/openid-configuration") {
                    call.respond(
                        AuthorizationServerMetadata(
                            issuer = issuerBase,
                            authorizationEndpoint = "$issuerBase/authorize",
                            tokenEndpoint = "$issuerBase/token",
                            responseTypesSupported = setOf("code", "token"),
                            grantTypesSupported = setOf("urn:ietf:params:oauth:grant-type:pre-authorized_code")
                        )
                    )
                }
                get("/credential-offer") {
                    call.respond(offer)
                }
                post("/token") {
                    val params = call.receiveParameters()
                    val code = params["pre-authorized_code"]
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            buildJsonObject { put("error", "invalid_request") }
                        )
                    val tokenRequestParams = mapOf(
                        "grant_type" to listOf("urn:ietf:params:oauth:grant-type:pre-authorized_code"),
                        "pre-authorized_code" to listOf(code),
                        "client_id" to listOf(params["client_id"] ?: "wallet-client")
                    )
                    val accessTokenRequest = provider.createAccessTokenRequest(tokenRequestParams)
                    if (accessTokenRequest !is AccessTokenRequestResult.Success) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            buildJsonObject { put("error", "invalid_grant") }
                        )
                    }
                    val tokenResponse = provider.createAccessTokenResponse(
                        accessTokenRequest.request.withIssuer(issuerBase)
                    )
                    if (tokenResponse !is AccessTokenResponseResult.Success) {
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            buildJsonObject { put("error", "server_error") }
                        )
                    }
                    call.respond(buildJsonObject {
                        put("access_token", tokenResponse.response.accessToken)
                        put("token_type", "Bearer")
                        put("c_nonce", "test-nonce")
                        put("c_nonce_expires_in", 300)
                    })
                }
                post("/credential") {
                    val rawBody = call.receiveText()
                    println("=== CREDENTIAL ENDPOINT BODY: $rawBody")
                    val body = json.parseToJsonElement(rawBody).jsonObject
                    val configId = body["credential_configuration_id"]?.jsonPrimitive?.content
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            buildJsonObject { put("error", "invalid_request") }
                        )
                    println("=== proofs element: ${body["proofs"]}")
                    println("=== proof element: ${body["proof"]}")

                    // proofs may arrive as a JSON object {"jwt": ["..."]} or as JsonNull if not provided
                    val proofsElement = body["proofs"]
                    val proofsObj = proofsElement
                        ?.takeIf { it !is JsonNull }
                        ?.let { it as? JsonObject }
                    val proofJwt = proofsObj?.get("jwt")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                        ?: body["proof"]?.takeIf { it !is JsonNull }
                            ?.jsonObject?.get("jwt")?.jsonPrimitive?.content

                    val credentialRequestResult = provider.createCredentialRequest(
                        parameters = mapOf(
                            "credential_configuration_id" to listOf(configId),
                            "proofs" to listOf(
                                buildJsonObject {
                                    put("jwt", buildJsonArray {
                                        proofJwt?.let { add(JsonPrimitive(it)) }
                                    })
                                }.toString()
                            )
                        ),
                        session = session
                    )
                    if (credentialRequestResult !is CredentialRequestResult.Success) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            buildJsonObject { put("error", "invalid_proof") }
                        )
                    }
                    val credentialResponse = provider.createCredentialResponse(
                        request = credentialRequestResult.request.withIssuer(issuerBase),
                        configuration = configuration,
                        issuerKey = issuerKey,
                        issuerId = issuerBase,
                        credentialData = credentialData,
                        selectiveDisclosure = selectiveDisclosure
                    )
                    if (credentialResponse !is CredentialResponseResult.Success) {
                        val failure = credentialResponse as CredentialResponseResult.Failure
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            buildJsonObject {
                                put("error", failure.error.error)
                                put("error_description", failure.error.description ?: "no description")
                            }
                        )
                    }
                    val httpResponse = provider.writeCredentialResponse(
                        credentialRequestResult.request.withIssuer(issuerBase),
                        credentialResponse.response
                    )
                    call.respond(HttpStatusCode.fromValue(httpResponse.status), httpResponse.payload)
                }
            }
        }.start(wait = false)

        try {
            E2ETest("127.0.0.1", walletPort, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog, OSSVerifier2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig(
                        "wallet-service",
                        OSSWallet2ServiceConfig(publicBaseUrl = Url(walletBase))
                    )
                    ConfigManager.preloadConfig(
                        "verifier-service",
                        OSSVerifier2ServiceConfig(
                            clientId = "test-verifier-$tag",
                            clientMetadata = ClientMetadata(clientName = "Test Verifier"),
                            urlPrefix = "$walletBase/verification-session",
                            urlHost = "openid4vp://authorize"
                        )
                    )
                },
                init = {
                    DidService.minimalInit()
                },
                module = { combinedWalletVerifierModule() }
            ) {
                val http = testHttpClient()

                // 1. Create wallet
                val walletId = testAndReturn("[$tag] Create wallet") {
                    http.post("/wallet") {
                        setBody(CreateWalletRequest())
                    }.also { assertEquals(HttpStatusCode.Created, it.status) }
                        .body<WalletCreatedResponse>().walletId
                }

                // 1b. Generate a key — required for proof-of-possession during issuance
                //     and for presentation signing
                val keyInfo = testAndReturn("[$tag] Generate key for wallet") {
                    http.post("/wallet/$walletId/keys/generate") {
                        setBody(id.walt.wallet2.server.handlers.GenerateKeyRequest(keyType = "Ed25519"))
                    }.also { assertEquals(HttpStatusCode.Created, it.status) }
                        .body<WalletKeyInfo>()
                }

                // 1c. Register a did:key bound to the generated key — required for VP signing
                val holderDid = testAndReturn("[$tag] Register did:key for wallet") {
                    http.post("/wallet/$walletId/dids/create") {
                        setBody(id.walt.wallet2.server.handlers.CreateDidRequest(method = "key", keyId = keyInfo.keyId))
                    }.also { assertEquals(HttpStatusCode.Created, it.status) }
                        .body<id.walt.wallet2.data.WalletDidEntry>().did
                }

                // 2. Receive credential from the OID4VCI 1.0 issuer via pre-auth code
                val offerUri = "openid-credential-offer://?credential_offer_uri=${
                    "$issuerBase/credential-offer".encodeURLParameter()
                }"
                val receiveResult = testAndReturn("[$tag] Wallet receives credential (OID4VCI 1.0)") {
                    http.post("/wallet/$walletId/credentials/receive") {
                        setBody(ReceiveCredentialRequest(offerUrl = Url(offerUri)))
                    }.also {
                        assertEquals(
                            HttpStatusCode.OK, it.status,
                            "Receive failed: ${it.body<String>()}"
                        )
                    }.body<ReceiveCredentialResult>()
                        .also { assertTrue(it.credentialIds.isNotEmpty(), "No credential stored") }
                }

                // 3. Credential is visible in wallet
                testAndReturn("[$tag] Credential visible in wallet") {
                    val creds = http.get("/wallet/$walletId/credentials")
                        .body<List<StoredCredentialMetadata>>()
                    assertTrue(creds.isNotEmpty())
                    assertEquals(receiveResult.credentialIds.size, creds.size)
                }

                // 4. Verifier creates a verification session
                val verifierSession = testAndReturn("[$tag] Verifier creates session") {
                    http.post("/verification-session/create") {
                    setBody(CrossDeviceFlowSetup(core = GeneralFlowConfig(
                        dcqlQuery = dcqlQuery,
                        // Disable VC signature verification: the in-process test issuer has no
                        // resolvable public key at its URL, so the default signature policy fails.
                        policies = DefinedVerificationPolicies(vc_policies = VCPolicyList(policies = emptyList()))
                    )) as VerificationSessionSetup)
                    }.also { assertEquals(HttpStatusCode.OK, it.status) }
                        .body<VerificationSessionCreationResponse>()
                }
                val sessionId = verifierSession.sessionId
                val bootstrapUrl = verifierSession.bootstrapAuthorizationRequestUrl
                    ?: Url("$walletBase/verification-session/$sessionId/request")

                // 5. Wallet presents to verifier using the bootstrapAuthorizationRequestUrl
                // (which contains request_uri=... so WalletPresentFunctionality2 fetches the
                // full AuthorizationRequest including dcql_query from the server)
                testAndReturn("[$tag] Wallet presents credential (OID4VP 1.0)") {
                    http.post("/wallet/$walletId/credentials/present") {
                        setBody(PresentCredentialRequest(requestUrl = bootstrapUrl, did = holderDid))
                    }.also {
                        assertEquals(HttpStatusCode.OK, it.status, "Present failed: ${it.body<String>()}")
                    }
                }

                // 6. Verify session outcome
                testAndReturn("[$tag] Verification session is SUCCESSFUL") {
                    val verifierSession = http.get("/verification-session/$sessionId/info")
                        .body<Verification2Session>()
                    assertEquals(
                        Verification2Session.VerificationSessionStatus.SUCCESSFUL,
                        verifierSession.status,
                        "Session ended with ${verifierSession.status}: ${verifierSession.statusReason}"
                    )
                    assertTrue(
                        verifierSession.presentedCredentials?.isNotEmpty() == true,
                        "No presented credentials on session"
                    )
                }
            }
        } finally {
            issuerServer.stop(1000, 3000)
        }
    }
}

/** Combined module: wallet2 + verifier2 routes on the same Ktor app. */
private fun Application.combinedWalletVerifierModule() {
    // withPlugins = false: WebService wrapper already installs ContentNegotiation and StatusPages.
    // We only add SSE explicitly because verifier2 needs it and it's not in the WebService wrapper.
    install(io.ktor.server.sse.SSE)
    wallet2Module(withPlugins = false)
    verifierApi()
}
