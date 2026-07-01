package id.walt.openid4vp.conformance.testplans

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.vp.verifier.MdlX509SanDnsRequestUriSignedDirectPost
import id.walt.openid4vp.conformance.testplans.plans.vp.verifier.SdJwtVcX509SanDnsRequestUriSignedDirectPost
import id.walt.openid4vp.conformance.testplans.plans.TestPlan
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.runner.TestPlanRunner
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertNotNull

/**
 * OpenID4VP Verifier Conformance Test Runner
 * 
 * Runs the conformance suite against an already-running verifier-api2 service.
 * The verifier must be exposed via ngrok for the conformance suite to reach it.
 * 
 * Prerequisites:
 * 1. Start verifier-api2: ./gradlew :waltid-services:waltid-verifier-api2:run
 * 2. Start ngrok: ngrok http 7003
 * 3. Set VERIFIER_NGROK_URL environment variable to the ngrok HTTPS URL
 * 4. Start conformance suite (Docker)
 */
class VerifierConformanceTestRunner(
    private val verifierNgrokUrl: String,
    val conformanceHost: String = "localhost.emobix.co.uk",
    val conformancePort: Int = 8443
) {
    private val http = HttpClient {
        defaultRequest {
            url(verifierNgrokUrl)
        }
        install(ContentNegotiation) {
            json()
        }
    }

    private val testPlans: List<TestPlan> by lazy {
        // Use ngrok URL as the verifier prefix
        val verifier2UrlPrefix = "$verifierNgrokUrl/openid4vc/verify"
        listOf(
            MdlX509SanDnsRequestUriSignedDirectPost(verifier2UrlPrefix, conformanceHost, conformancePort),
            SdJwtVcX509SanDnsRequestUriSignedDirectPost(verifier2UrlPrefix, conformanceHost, conformancePort)
        )
    }

    suspend fun run(): List<TestPlanResult> {
        // 1. Check conformance suite is available
        val conformance = ConformanceInterface(conformanceHost, conformancePort)
        val conformanceVersion = conformance.getServerVersion()
        assertNotNull(conformanceVersion)
        println("✅ Conformance server version $conformanceVersion available!")

        // 2. Check verifier is reachable via ngrok
        checkVerifierReachable()

        // 3. Run all test plans
        return testPlans.flatMap { plan ->
            val planName = plan::class.simpleName ?: plan::class.jvmName
            println("\n" + "=".repeat(60))
            println("Running verifier plan: $planName")
            println("=".repeat(60))
            
            try {
                listOf(TestPlanRunner(plan.config, http, conformanceHost, conformancePort, planName).test())
            } catch (e: Exception) {
                println("❌ Test plan $planName failed with exception: ${e.message}")
                listOf(
                    TestPlanResult(
                        testName = planName,
                        conformanceTestId = "N/A",
                        conformanceResult = "ERROR",
                        errorMessage = e.message
                    )
                )
            }
        }
    }

    private suspend fun checkVerifierReachable() {
        try {
            val response = http.get("$verifierNgrokUrl/openid4vc/verify") {
                // Just check if the endpoint responds (404 is fine, means route exists)
            }
            if (response.status == HttpStatusCode.NotFound || response.status.isSuccess()) {
                println("✅ Verifier reachable at $verifierNgrokUrl")
            } else {
                println("⚠️ Verifier responded with ${response.status} - continuing anyway")
            }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Cannot reach verifier at $verifierNgrokUrl. " +
                "Ensure verifier-api2 is running and ngrok is forwarding to port 7003.\n" +
                "Error: ${e.message}"
            )
        }
    }

    fun close() {
        http.close()
    }
}
