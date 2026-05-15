package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.runner.req.IssuerTestPlanConfiguration
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.assertEquals

/**
 * Runner for OpenID4VCI Issuer conformance tests.
 * 
 * In this scenario:
 * - Our issuer is the System Under Test (SUT)
 * - The conformance suite acts as a wallet
 * - The conformance suite will call our issuer endpoints
 */
class IssuerTestPlanRunner(
    val config: IssuerTestPlanConfiguration,
    http: HttpClient,

    val conformanceHost: String,
    val conformancePort: Int
) {
    companion object {
        val baseUrlBuilderSetup: URLBuilder.(host: String, port: Int) -> Unit = { cHost, cPort ->
            protocol = URLProtocol.HTTPS
            host = cHost
            port = cPort
        }
    }

    private val conformanceHttp = HttpClient() {
        followRedirects = false

        defaultRequest {
            url {
                baseUrlBuilderSetup(conformanceHost, conformancePort)
            }
        }
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    val conformance = ConformanceInterface(conformanceHost, conformancePort)

    suspend fun test(): TestPlanResult {
        println("-- Conformance OID4VCI Issuer Test -- -> Setup")

        // Create test plan
        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig(
            config.testPlanCreationUrl
        )

        println("Creating issuer test plan... ($createTestPlanUrl)")
        val createTestPlanResponse = conformance.createTestPlan(createTestPlanUrl, config.testPlanCreationConfiguration)

        if (createTestPlanResponse.modules.isEmpty()) {
            throw IllegalStateException("No test modules available for the specified variant. Check your configuration.")
        }

        if (createTestPlanResponse.modules.size > 1) {
            println("NOTICE: Multiple test modules available: ${createTestPlanResponse.modules.map { it.testModule }}")
        }

        val testPlanId = createTestPlanResponse.id
        val testModule = createTestPlanResponse.modules.first().testModule
        println("Created test plan: $testPlanId")
        println("Test module: $testModule")

        // Create test
        val createTestUrl = conformance.buildCreateTestUrl(testPlanId, testModule)
        println("Creating test... ($createTestUrl)")
        val createTestResponse = conformance.createTest(createTestUrl)
        println()

        val testId = createTestResponse.id
        println("Created test: $testId")

        println("View test run at: https://$conformanceHost:$conformancePort/log-detail.html?log=${testId}")

        // For issuer tests, the conformance suite (acting as wallet) will:
        // 1. Call our issuer's /.well-known/openid-credential-issuer endpoint
        // 2. Initiate the credential issuance flow
        // 3. Call our authorization endpoint (if using auth code flow)
        // 4. Exchange tokens
        // 5. Request credentials
        
        println("Waiting for conformance suite to complete issuer tests...")
        println("The conformance suite will call our issuer at: ${config.issuerUrl}")
        
        // Wait for the test to complete - for issuer tests, the conformance suite drives the flow
        conformance.waitForTestStatus(testId, shouldBeWaiting = false)

        val testRunInfo = conformance.getTestRunInfo(testId)
        println("Test run result: $testRunInfo")
        println("Status: ${testRunInfo.status}")
        println("Result: ${testRunInfo.result}")

        // For issuer tests, we mainly care about the conformance suite result
        assertEquals("FINISHED", testRunInfo.status, "Test should finish")
        assertEquals("PASSED", testRunInfo.result, "Test should pass")

        return TestPlanResult(
            conformanceTestId = testId,
            conformanceStatus = testRunInfo.status,
            conformanceResult = testRunInfo.result,
            verifierStatus = null // Not applicable for issuer tests
        )
    }
}
