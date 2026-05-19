package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.http.Verifier2Interface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.runner.req.ExpectedVerifierOutcome
import id.walt.openid4vp.conformance.testplans.runner.req.TestPlanConfiguration
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.assertEquals

class TestPlanRunner(
    val config: TestPlanConfiguration,
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
    val verifier2 = Verifier2Interface(http)

    suspend fun test(): List<TestPlanResult> {
        println("-- Conformance -- -> Setup")

        // Create test plan
        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig(config.testPlanCreationUrl)
        println("Creating test plan... ($createTestPlanUrl)")
        val createTestPlanResponse = conformance.createTestPlan(createTestPlanUrl, config.testPlanCreationConfiguration)

        val testPlanId = createTestPlanResponse.id
        println("Created test plan: $testPlanId with ${createTestPlanResponse.modules.size} modules")

        val results = mutableListOf<TestPlanResult>()

        for (module in createTestPlanResponse.modules) {
            val testModule = module.testModule
            val expectedOutcome = config.moduleOutcomes[testModule]
                ?: error("No expected outcome configured for test module: $testModule")

            println("\n=== Running module: $testModule (expected: $expectedOutcome) ===")

            val result = runModule(testPlanId, testModule, expectedOutcome)
            results += result
            println("Module $testModule result: conformance=${result.conformanceResult}, verifier=${result.verifierStatus}")
        }

        return results
    }

    private suspend fun runModule(
        testPlanId: String,
        testModule: String,
        expectedOutcome: ExpectedVerifierOutcome
    ): TestPlanResult {
        // Create test
        val createTestUrl = conformance.buildCreateTestUrl(testPlanId, testModule, config.moduleVariant)
        println("Creating test... ($createTestUrl)")
        val createTestResponse = conformance.createTest(createTestUrl)
        val testId = createTestResponse.id
        println("Created test: $testId")
        println("View test run at: https://$conformanceHost:$conformancePort/log-detail.html?log=${testId}")

        // Wait for test to be ready
        conformance.waitForTestStatus(testId, shouldBeWaiting = true)

        val testRunResult = conformance.getTestRun(testId)
        val authorizationEndpointToUse = testRunResult.getExposedAuthorizationEndpoint().replace("http://", "https://")
        println("Use authorization endpoint: $authorizationEndpointToUse")

        // Create verification session
        val verificationSessionResponse = verifier2.createVerificationSession(
            authorizationEndpointToUse,
            config.verificationSessionSetup
        )
        val verificationSessionId = verificationSessionResponse.sessionId
        println("Created Verification Session: $verificationSessionId")

        // Trigger presentation: conformance suite acts as wallet, fetches the request
        val bootstrapRequestUrl = verificationSessionResponse.bootstrapAuthorizationRequestUrl!!
        conformanceHttp.get(bootstrapRequestUrl)

        // Wait for conformance to finish processing
        println("Waiting until conformance processing is done...")
        conformance.waitForTestStatus(testId, shouldBeWaiting = false)

        val testRunInfo = conformance.getTestRunInfo(testId)
        println("Conformance result: status=${testRunInfo.status}, result=${testRunInfo.result}")

        val verifier2Session = verifier2.getVerificationSessionInfo(verificationSessionId)
        println("Verifier2 session status: ${verifier2Session.status}")

        assertEquals("FINISHED", testRunInfo.status, "Conformance test $testModule did not finish")

        when (expectedOutcome) {
            ExpectedVerifierOutcome.ACCEPT -> {
                assertEquals("PASSED", testRunInfo.result, "Conformance test $testModule expected PASSED")
                assertEquals(
                    VerificationSessionStatus.SUCCESSFUL, verifier2Session.status,
                    "Verifier2 session for $testModule expected SUCCESSFUL"
                )
            }

            ExpectedVerifierOutcome.REJECT -> {
                assertEquals("PASSED", testRunInfo.result, "Conformance test $testModule expected PASSED")
                assertEquals(
                    VerificationSessionStatus.FAILED, verifier2Session.status,
                    "Verifier2 session for $testModule expected FAILED (verifier should have rejected)"
                )
            }

            ExpectedVerifierOutcome.ACCEPT_OR_SKIP -> {
                require(
                    testRunInfo.result in listOf("PASSED", "SKIPPED")
                ) {
                    "Conformance test $testModule expected PASSED or SKIPPED, got ${testRunInfo.result}"
                }
                if (testRunInfo.result == "PASSED") {
                    assertEquals(
                        VerificationSessionStatus.SUCCESSFUL, verifier2Session.status,
                        "Verifier2 session for $testModule expected SUCCESSFUL"
                    )
                }
            }
        }

        return TestPlanResult(
            conformanceTestId = testId,
            conformanceStatus = testRunInfo.status,
            conformanceResult = testRunInfo.result,
            verifierStatus = verifier2Session.status,
        )
    }
}
