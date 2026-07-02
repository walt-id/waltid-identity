package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.adapter.VciWalletConformanceAdapter
import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.runner.VciWalletTestPlanRunner
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.VciWalletSdJwtDpop
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

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
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
 *     --tests "VciWalletConformanceTests" -PrunIntegrationTests
 * ```
 */
class VciWalletConformanceTests {

    companion object {
        private val walletApiUrl = ConformanceConfig.WALLET_API_URL
        private val adapterPort = 7007
        private val conformanceHost = ConformanceConfig.CONFORMANCE_HOST
        private val conformancePort = ConformanceConfig.CONFORMANCE_PORT

        // Get host IP for Docker container access (host.docker.internal doesn't work on Linux)
        private val adapterHostIp = getHostIp()

        private fun getHostIp(): String {
            return try {
                val process = ProcessBuilder("sh", "-c", "ip route get 1.1.1.1 | awk '{print \$7; exit}'")
                    .redirectErrorStream(true)
                    .start()
                val ip = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                if (ip.isNotEmpty() && ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                    println("[VCI Test] Host IP: $ip")
                    ip
                } else {
                    println("[VCI Test] Could not determine host IP, using 127.0.0.1")
                    "127.0.0.1"
                }
            } catch (_: Exception) {
                "127.0.0.1"
            }
        }

        private val conformanceAvailable = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println("""
                    |
                    | Conformance suite not available.
                    | To run these tests:
                    |   1. cd ~/dev/openid/conformance-suite
                    |   2. docker compose -f docker-compose-walt.yml up -d
                    |   3. Wait ~30s for startup
                    |
                """.trimMargin())
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
     */
    @Test
    fun `VCI Wallet - SD-JWT VC + DPoP + authorization_code`() = runBlocking {
        val httpClient = createHttpClient()

        // Check if standalone adapter is already running
        val adapterAlreadyRunning = try {
            val response = httpClient.get("http://127.0.0.1:$adapterPort/health")
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }

        val adapter: VciWalletConformanceAdapter?
        if (adapterAlreadyRunning) {
            println("[VCI Test] Using existing adapter on port $adapterPort")
            adapter = null
        } else {
            println("[VCI Test] Starting adapter on port $adapterPort")
            adapter = VciWalletConformanceAdapter(
                walletApiUrl = walletApiUrl,
                adapterPort = adapterPort
            )
            adapter.start(httpClient)
        }

        val credentialOfferEndpoint = "http://127.0.0.1:$adapterPort/credential-offer"
        val redirectUri = "http://127.0.0.1:$adapterPort/callback"

        try {
            val plan = VciWalletSdJwtDpop(
                walletApiUrl = walletApiUrl,
                credentialOfferEndpoint = credentialOfferEndpoint,
                redirectUri = redirectUri,
                conformanceHost = conformanceHost,
                conformancePort = conformancePort,
                adapterHost = adapterHostIp
            )

            val runner = VciWalletTestPlanRunner(
                testPlan = plan,
                conformanceHost = conformanceHost,
                conformancePort = conformancePort,
                walletHttpClient = httpClient,
                walletAdapterUrl = "http://127.0.0.1:$adapterPort"
            )

            runner.test()

        } finally {
            adapter?.stop()
            httpClient.close()
        }
        Unit  // JUnit 5 requires void return
    }
}
