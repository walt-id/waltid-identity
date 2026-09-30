@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.conformance

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.did.dids.DidService
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.openid4vp.conformance.adapter.VciWalletConformanceAdapter
import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.keys.ClientAttestationTestAuthority
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.*
import id.walt.openid4vp.conformance.testplans.runner.VciWalletTestPlanRunner
import id.walt.wallet2.OSSWallet2FeatureCatalog
import id.walt.wallet2.OSSWallet2ServiceConfig
import id.walt.wallet2.WalletAttestationConfig
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierApi
import id.walt.wallet2.data.HolderKeyBinding
import id.walt.wallet2.data.StoredCredentialMetadata
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.handlers.BuildVpTokenRequest
import id.walt.wallet2.handlers.BuildVpTokenResult
import id.walt.wallet2.handlers.SendAuthorizationResponseRequest
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.ImportKeyRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import id.walt.wallet2.wallet2Module
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.install
import kotlinx.coroutines.runBlocking
import id.waltid.openid4vci.wallet.attestation.PUBLIC_JWK_PLACEHOLDER
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises Wallet2 against the OpenID suite acting as issuer. Wallet2 and the callback adapter
 * run in-process; the batch gate also hosts Verifier2 to prove both stored credentials are usable.
 * See docs/VCI-WALLET.md for suite setup, profile scope, commands and evidence boundaries.
 */
class VciWalletConformanceTests {

    companion object {
        /** Request field the adapter's attester reads the wallet's public JWK from. */
        private const val ATTESTER_REQUEST_JWK_FIELD = "jwk"

        private const val WALLET_HOST = "127.0.0.1"
        private const val WALLET_PORT = 7016

        /** Wallet2 runs in-process, so its URL is derived rather than configured. */
        private val walletApiUrl = "http://$WALLET_HOST:$WALLET_PORT"
        private val adapterPort = 7007
        private val conformanceHost = ConformanceConfig.CONFORMANCE_HOST
        private val conformancePort = ConformanceConfig.CONFORMANCE_PORT

        /** Host the conformance suite must call the adapter on; see [ConformanceConfig.ADAPTER_CALLBACK_HOST]. */
        private val adapterHostIp = ConformanceConfig.ADAPTER_CALLBACK_HOST

        private val conformanceAvailable = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println(
                    """
                    |
                    | Conformance suite not available.
                    | To run these tests:
                    |   1. cd ~/dev/openid/conformance-suite
                    |   2. docker compose -f docker-compose-walt.yml up -d
                    |   3. Wait ~30s for startup
                    |
                """.trimMargin()
                )
            }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceAvailable.isSuccess

        @JvmStatic
        @AfterAll
        fun writeSkippedSummaryIfSuiteUnavailable() {
            if (isConformanceAvailable) return
            ConformanceReportWriter.writeSkippedIfEmpty(
                role = ConformanceReportWriter.Role.VCI_WALLET,
                reason = "Conformance suite not available at $conformanceHost:$conformancePort",
            )
        }

        init {
            println()
            println("═".repeat(60))
            println(" VCI Wallet Conformance Tests")
            println("═".repeat(60))
            if (isConformanceAvailable) {
                println(" Conformance suite: ${conformanceAvailable.getOrNull()}")
            } else {
                println(" Conformance suite: NOT AVAILABLE (tests will be skipped)")
            }
            println(" Wallet API: $walletApiUrl")
            println(" Adapter port: $adapterPort")
            println(" Adapter host IP: $adapterHostIp")
            println("═".repeat(60))
            println()
        }

        private fun createHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
            }
            expectSuccess = false
        }
    }

    private suspend fun runPlan(
        plan: VciWalletTestPlan,
        walletId: String,
        attestationAuthority: ClientAttestationTestAuthority? = null,
        batchHolderKeyIds: List<String> = emptyList(),
        clientKeyId: String? = null,
        requiredModules: Set<String> = emptySet(),
        useScope: Boolean = plan.isHaip,
        requiredVariant: Map<String, String> = emptyMap(),
    ): List<TestPlanResult> {
        val httpClient = createHttpClient()
        val adapter = startAdapterIfNeeded(httpClient, walletId, attestationAuthority, batchHolderKeyIds, clientKeyId, useScope)
        val adapterBaseUrl = "http://127.0.0.1:$adapterPort"

        try {
            val runner = VciWalletTestPlanRunner(
                testPlan = plan,
                conformanceHost = conformanceHost,
                conformancePort = conformancePort,
                walletHttpClient = httpClient,
                walletAdapterUrl = adapterBaseUrl
            )

            return runner.test(requiredModules, requiredVariant)
        } finally {
            adapter?.stop()
            httpClient.close()
        }
    }

    private suspend fun startAdapterIfNeeded(
        httpClient: HttpClient,
        walletId: String,
        attestationAuthority: ClientAttestationTestAuthority?,
        batchHolderKeyIds: List<String>,
        clientKeyId: String?,
        useScope: Boolean,
    ): VciWalletConformanceAdapter? {
        val adapterAlreadyRunning = try {
            val response = httpClient.get("http://127.0.0.1:$adapterPort/health")
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }

        if (adapterAlreadyRunning) {
            check(batchHolderKeyIds.isEmpty()) { "Batch conformance requires its own adapter and holder keys" }
            println("[VCI Test] Using existing adapter on port $adapterPort")
            return null
        }

        println("[VCI Test] Starting adapter on port $adapterPort")
        return VciWalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort,
            walletId = walletId,
            attestationAuthority = attestationAuthority,
            useScope = useScope,
            batchHolderKeyIds = batchHolderKeyIds,
            testKeyId = clientKeyId,
        ).also { it.start(httpClient) }
    }

    @Test
    fun vciWalletBatchBothGrantsAndFormats() {
        check(isConformanceAvailable) { "The pinned suite is required for wallet batch acceptance" }
        runBlocking {
            ConformanceInterface(conformanceHost, conformancePort).use { suite ->
                val version = suite.conformanceHttp.get("/api/server").body<JsonObject>()
                assertEquals("db1080a", version["revision"]?.jsonPrimitive?.content)
                assertEquals("5.2.4", version["version"]?.jsonPrimitive?.content)
            }
        }
        for (format in listOf("sd_jwt_vc", "mdoc")) {
            for (grant in listOf("authorization_code", "pre_authorization_code")) {
                for (profile in listOf("rar", "simple", "haip")) {
                    if (profile == "haip" && (format != "sd_jwt_vc" || grant != "authorization_code")) continue
                    // db1080a's VCIInjectRequestScopePreAuthorizedCodeFlow hard-codes the SD-JWT
                    // scope, granting a different configuration from its mdoc offer.
                    if (format == "mdoc" && grant == "pre_authorization_code" && profile == "simple") continue
                    withInProcessWallet(withVerifier = true) { walletId ->
                        val attester = if (profile == "haip") ClientAttestationTestAuthority.create(
                            clientId = ConformanceConfig.VCI_WALLET_CLIENT_ID) else null
                        val base = if (attester != null) VciWalletSdJwtHaip(
                            walletApiUrl, "http://127.0.0.1:$adapterPort/credential-offer",
                            "http://127.0.0.1:$adapterPort/callback", conformanceHost, conformancePort, adapterHostIp,
                            attestationAuthority = attester,
                        ) else if (format == "mdoc") VciWalletMdocDpop(
                            walletApiUrl, "http://127.0.0.1:$adapterPort/credential-offer",
                            "http://127.0.0.1:$adapterPort/callback", conformanceHost, conformancePort, adapterHostIp,
                        ) else VciWalletSdJwtDpop(
                            walletApiUrl, "http://127.0.0.1:$adapterPort/credential-offer",
                            "http://127.0.0.1:$adapterPort/callback", conformanceHost, conformancePort, adapterHostIp,
                        )
                        val plan = object : VciWalletTestPlan {
                            override val description = "Wallet batch: $format / $grant / $profile / two distinct holders"
                            override val configuration = base.configuration
                            override val planName = base.planName
                            override val isHaip = base.isHaip
                            override val clientAuthType = base.clientAuthType
                            override val variant = if (isHaip) base.variant else base.variant + mapOf(
                                "vci_grant_type" to grant, "authorization_request_type" to profile)
                            override val producerId get() = "batch/${super<VciWalletTestPlan>.producerId}"
                        }
                        createHttpClient().use { client ->
                            val clientKeyId = client.get("$walletApiUrl/wallet/$walletId/keys")
                                .body<List<WalletKeyInfo>>().single().keyId
                            val holders = List(2) {
                                client.post("$walletApiUrl/wallet/$walletId/keys/generate") {
                                    contentType(ContentType.Application.Json)
                                    setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(keyType = KeyType.secp256r1))
                                }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<WalletKeyInfo>().keyId
                            }
                            val result = runPlan(plan, walletId, attestationAuthority = attester,
                                batchHolderKeyIds = holders, clientKeyId = clientKeyId, useScope = profile != "rar",
                                requiredModules = setOf("oid4vci-1_0-wallet-test-batch-credential-issuance"),
                                requiredVariant = mapOf("vci_credential_issuance_mode" to "immediate",
                                    "vci_credential_encryption" to "plain")).single()
                            assertEquals("FINISHED", result.conformanceStatus)
                            if (result.conformanceResult == "WARNING") {
                                // Hosted tunnels omit TLS evidence headers. Keep WARNING in the report;
                                // accept only those missing-header warnings, never a TLS-policy failure.
                                val warnings = ConformanceInterface(conformanceHost, conformancePort).use { suite ->
                                    suite.getTestLog(result.conformanceTestId).filter { it.result == "WARNING" }
                                }
                                assertTrue(warnings.isNotEmpty(), "A suite WARNING requires its diagnostic evidence")
                                val missingTlsHeaders = setOf(
                                    "EnsureIncomingTls12WithSecureCipherOrTls13" to
                                        "TLS Protocol not found; this header should have been set by the apache proxy",
                                    "EnsureIncomingTls13" to
                                        "TLS protocol not found; this header should have been set by the nginx proxy",
                                )
                                assertEquals(emptyList(), warnings.filter { (it.src to it.msg) !in missingTlsHeaders },
                                    "Batch acceptance only tolerates missing tunnel TLS headers")
                            } else {
                                assertEquals("PASSED", result.conformanceResult, result.errorMessage)
                            }
                            val credentials = client.get("$walletApiUrl/wallet/$walletId/credentials")
                                .body<List<StoredCredentialMetadata>>()
                            assertEquals(2, credentials.size)
                            val bindings = credentials.map {
                                val stored = client.get("$walletApiUrl/wallet/$walletId/credentials/${it.id}")
                                    .body<JsonObject>()
                                Json.decodeFromJsonElement<HolderKeyBinding>(assertNotNull(stored["holderKeyBinding"]))
                            }
                            assertEquals(2, bindings.map { it.publicKeyThumbprint }.distinct().size)
                            assertEquals(2, bindings.map { it.keyReference }.distinct().size)
                            for (credential in credentials) {
                                presentStoredCredential(client, walletId, credential.id, format)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Each selection must resolve its persisted holder key; no signing override is supplied. */
    private suspend fun presentStoredCredential(client: HttpClient, walletId: String, credentialId: String, format: String) {
        val query = CredentialQuery(
            id = "pid",
            format = if (format == "mdoc") CredentialFormat.MSO_MDOC else CredentialFormat.DC_SD_JWT,
            meta = if (format == "mdoc") MsoMdocMeta(doctypeValue = "eu.europa.ec.eudi.pid.1")
                else SdJwtVcMeta(vctValues = listOf("urn:eudi:pid:1")),
        )
        val session = client.post("$walletApiUrl/verification-session/create") {
            contentType(ContentType.Application.Json)
            setBody<VerificationSessionSetup>(CrossDeviceFlowSetup(core = GeneralFlowConfig(
                dcqlQuery = DcqlQuery(credentials = listOf(query)),
            )))
        }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
            .body<VerificationSessionCreationResponse>()
        val requestUrl = assertNotNull(session.bootstrapAuthorizationRequestUrl)
        val presentation = client.post("$walletApiUrl/wallet/$walletId/credentials/present/build-vp-token") {
            contentType(ContentType.Application.Json)
            setBody(BuildVpTokenRequest(requestUrl, selectedCredentialIds = mapOf("pid" to listOf(credentialId))))
        }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }.body<BuildVpTokenResult>()
        client.post("$walletApiUrl/wallet/$walletId/credentials/present/send-response") {
            contentType(ContentType.Application.Json)
            setBody(SendAuthorizationResponseRequest(requestUrl, presentation.vpToken, presentation.idToken))
        }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
        val result = client.get("$walletApiUrl/verification-session/${session.sessionId}/info").body<JsonObject>()
        assertEquals("SUCCESSFUL", result["status"]?.jsonPrimitive?.content, result.toString())
    }

    /**
     * SD-JWT VC + DPoP + private_key_jwt + authorization_code
     *
     * Tests wallet's complete credential issuance flow:
     * 1. Receive credential offer from issuer
     * 2. Discover issuer metadata
     * 3. Initiate authorization code flow
     * 4. Exchange auth code for tokens with DPoP
     * 5. Request credential with proof
     * 6. Validate and store issued SD-JWT VC
     *
     * Uses credential configuration ID: eu.europa.ec.eudi.pid.1 (SD-JWT VC format)
     */
    /**
     * SD-JWT VC + DPoP + pre-authorized code.
     *
     * The grant Wallet2 can complete in a single call, so the adapter delegates rather than
     * orchestrating the exchange itself. See [VciWalletSdJwtPreAuth].
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun vciWalletSdJwtVcPreAuthorizedCode() = withInProcessWallet { walletId ->
        runPlan(
            VciWalletSdJwtPreAuth(
                walletApiUrl = walletApiUrl,
                credentialOfferEndpoint = "http://127.0.0.1:$adapterPort/credential-offer",
                redirectUri = "http://127.0.0.1:$adapterPort/callback",
                conformanceHost = conformanceHost,
                conformancePort = conformancePort,
                adapterHost = adapterHostIp,
            ),
            walletId,
        )
    }

    @Test
    @EnabledIf("isConformanceAvailable")
    fun vciWalletSdJwtVcDpopAuthorizationCode() = withInProcessWallet { walletId ->
        runPlan(
            VciWalletSdJwtDpop(
                walletApiUrl = walletApiUrl,
                credentialOfferEndpoint = "http://127.0.0.1:$adapterPort/credential-offer",
                redirectUri = "http://127.0.0.1:$adapterPort/callback",
                conformanceHost = conformanceHost,
                conformancePort = conformancePort,
                adapterHost = adapterHostIp
            ),
            walletId,
        )
    }

    /**
     * ISO mdoc + DPoP + private_key_jwt + authorization_code
     *
     * Tests wallet's ability to receive ISO 18013-5 mdoc credentials:
     * 1. Receive credential offer from issuer
     * 2. Discover issuer metadata
     * 3. Initiate authorization code flow
     * 4. Exchange auth code for tokens with DPoP
     * 5. Request credential with proof
     * 6. Validate and store issued ISO mdoc
     *
     * Uses credential configuration ID: eu.europa.ec.eudi.pid.mdoc.1 (mso_mdoc format)
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun vciWalletIsoMdocDpopAuthorizationCode() = withInProcessWallet { walletId ->
        runPlan(
            VciWalletMdocDpop(
                walletApiUrl = walletApiUrl,
                credentialOfferEndpoint = "http://127.0.0.1:$adapterPort/credential-offer",
                redirectUri = "http://127.0.0.1:$adapterPort/callback",
                conformanceHost = conformanceHost,
                conformancePort = conformancePort,
                adapterHost = adapterHostIp
            ),
            walletId,
        )
    }

    /**
     * SD-JWT VC + authorization_code (HAIP full target)
     *
     * Full HAIP wallet profile. The local harness should reach the conformance
     * suite and execute the full HAIP module set even when the wallet
     * implementation still fails the individual HAIP checks.
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun vciWalletSdJwtVcAuthorizationCodeHaipFullTarget() = withInProcessWallet { walletId ->
        // Minted per run: the suite is told the trust anchor, and the adapter signs with the matching
        // leaf, so nothing about the attester has to be committed or kept in sync by hand.
        val attestationAuthority = ClientAttestationTestAuthority.create(
            clientId = ConformanceConfig.VCI_WALLET_CLIENT_ID,
        )
        runPlan(
            VciWalletSdJwtHaip(
                walletApiUrl = walletApiUrl,
                credentialOfferEndpoint = "http://127.0.0.1:$adapterPort/credential-offer",
                redirectUri = "http://127.0.0.1:$adapterPort/callback",
                conformanceHost = conformanceHost,
                conformancePort = conformancePort,
                adapterHost = adapterHostIp,
                attestationAuthority = attestationAuthority,
            ),
            walletId,
            attestationAuthority,
        )
    }

    /**
     * Run [block] against a freshly created wallet in an in-process Wallet2.
     *
     * Mirrors how Verifier2 is hosted for the verifier suite: no separately launched service and no
     * separately configured wallet service. No credential is provisioned locally -
     * OpenID4VCI is about receiving one.
     *
     * A signing key is still required: the credential request carries a JWT proof of possession, so
     * with no key `receiveCredential` fails before it ever contacts the issuer. The wallet imports
     * [TestKeyMaterial.SUITE_WALLET_CLIENT_KEY] rather than generating one, because the same key also
     * signs the `private_key_jwt` client assertion and must therefore match the `client.jwks` the
     * plan registers with the suite.
     *
     * No DID is created - `buildJwtProof` binds the proof to the raw JWK when the wallet has no DID,
     * which is the natural binding for SD-JWT VC (`cnf.jwk`).
     */
    private fun withInProcessWallet(withVerifier: Boolean = false, block: suspend (walletId: String) -> Unit) {
        E2ETest(WALLET_HOST, WALLET_PORT, failEarly = true).testBlock(
            timeout = 30.minutes,
            features = if (withVerifier) listOf(OSSWallet2FeatureCatalog, OSSVerifier2FeatureCatalog)
                else listOf(OSSWallet2FeatureCatalog),
            preload = {
                if (withVerifier) ConfigManager.preloadConfig("verifier-service", OSSVerifier2ServiceConfig(
                    clientId = null,
                    clientMetadata = ClientMetadata(clientName = "Batch holder verification"),
                    urlPrefix = "$walletApiUrl/verification-session",
                    urlHost = "openid4vp://authorize",
                ))
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(
                        publicBaseUrl = Url(walletApiUrl),
                        // Configured for every plan, but inert unless the authorization server
                        // advertises attest_jwt_client_auth - only the HAIP plan does. Going through
                        // the real config path is the point: it is what builds the assembler the
                        // wallet uses, so a broken attestationConfig would otherwise pass unnoticed.
                        attestationConfig = WalletAttestationConfig(
                            attesterUrl = "http://127.0.0.1:$adapterPort" +
                                ConformanceConfig.VCI_WALLET_ATTESTATION_PATH,
                            requestBody = buildJsonObject {
                                put(ATTESTER_REQUEST_JWK_FIELD, PUBLIC_JWK_PLACEHOLDER)
                            },
                        ),
                    ),
                )
            },
            init = { DidService.minimalInit() },
            module = {
                wallet2Module(withPlugins = false)
                if (withVerifier) {
                    install(io.ktor.server.sse.SSE)
                    verifierApi()
                }
            },
        ) {
            val walletId = testAndReturn("Create wallet") {
                testHttpClient().post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest())
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletCreatedResponse>().walletId
            }

            test("Import the pre-registered client key the wallet authenticates with") {
                testHttpClient().post("/wallet/$walletId/keys/import") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        ImportKeyRequest(
                            key = Json.parseToJsonElement(
                                TestKeyMaterial.SUITE_WALLET_CLIENT_SERIALIZED_KEY
                            ).jsonObject
                        )
                    )
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
            }

            // Counted as an E2E step so a failing plan fails this JUnit test even if the runner
            // throws outside fail-early bookkeeping. failIfNeeded fails on unaccepted modules.
            test("Run OpenID4VCI wallet plan") {
                block(walletId)
            }
        }
    }
}
