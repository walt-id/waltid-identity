package id.walt.openid4vp.conformance

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.did.dids.DidService
import id.walt.openid4vp.conformance.adapter.VciWalletConformanceAdapter
import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.keys.ClientAttestationTestAuthority
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.*
import id.walt.openid4vp.conformance.testplans.runner.VciWalletTestPlanRunner
import id.walt.wallet2.OSSWallet2FeatureCatalog
import id.walt.wallet2.OSSWallet2ServiceConfig
import id.walt.wallet2.WalletAttestationConfig
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.ImportKeyRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import id.walt.wallet2.wallet2Module
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import id.waltid.openid4vci.wallet.attestation.PUBLIC_JWK_PLACEHOLDER
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * VCI Wallet Conformance Tests
 *
 * Tests wallet's ability to receive credentials from an OpenID4VCI issuer.
 * The OpenID conformance suite acts as the credential issuer.
 *
 * ## Test Profile
 *
 * Based on issuer-req.md requirements (wallet perspective):
 *
 * | Property | Value |
 * |----------|-------|
 * | Credential Format | sd_jwt_vc |
 * | Sender Constraint | dpop |
 * | Client Authentication | private_key_jwt |
 * | Grant Type | authorization_code |
 * | FAPI Profile | vci |
 *
 * ## Prerequisites
 *
 * 1. OpenID conformance suite running:
 *    ```bash
 *    cd ~/dev/openid/conformance-suite
 *    docker compose -f docker-compose-walt.yml up -d
 *    ```
 *
 * 2. wallet-api2 running:
 *    ```bash
 *    ./gradlew :waltid-services:waltid-wallet-api2:run
 *    ```
 *
 * 3. /etc/hosts entry:
 *    ```
 *    127.0.0.1 localhost.emobix.co.uk
 *    ```
 *
 * ## Run
 *
 * ```bash
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:vciWalletSdJwtVcAuthorizationCodeHaipFullTarget \
 *     -PrunIntegrationTests
 * ```
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
        useScope: Boolean = false,
    ) {
        val httpClient = createHttpClient()
        val adapter = startAdapterIfNeeded(httpClient, walletId, attestationAuthority, useScope)
        val adapterBaseUrl = "http://127.0.0.1:$adapterPort"

        try {
            val runner = VciWalletTestPlanRunner(
                testPlan = plan,
                conformanceHost = conformanceHost,
                conformancePort = conformancePort,
                walletHttpClient = httpClient,
                walletAdapterUrl = adapterBaseUrl
            )

            runner.test()
        } finally {
            adapter?.stop()
            httpClient.close()
        }
    }

    private suspend fun startAdapterIfNeeded(
        httpClient: HttpClient,
        walletId: String,
        attestationAuthority: ClientAttestationTestAuthority?,
        useScope: Boolean,
    ): VciWalletConformanceAdapter? {
        val adapterAlreadyRunning = try {
            val response = httpClient.get("http://127.0.0.1:$adapterPort/health")
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }

        if (adapterAlreadyRunning) {
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
        ).also { it.start(httpClient) }
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
            // The HAIP plan fixes authorization_request_type=simple.
            useScope = true,
        )
    }

    /**
     * Run [block] against a freshly created wallet in an in-process Wallet2.
     *
     * Mirrors how Verifier2 is hosted for the verifier suite: no separately launched service and no
     * fixed external port to coordinate. Unlike the presentation suite no *credential* is provisioned -
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
    private fun withInProcessWallet(block: suspend (walletId: String) -> Unit) {
        E2ETest(WALLET_HOST, WALLET_PORT, failEarly = true).testBlock(
            timeout = 30.minutes,
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
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
            module = { wallet2Module(withPlugins = false) },
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

            block(walletId)
        }
    }
}
