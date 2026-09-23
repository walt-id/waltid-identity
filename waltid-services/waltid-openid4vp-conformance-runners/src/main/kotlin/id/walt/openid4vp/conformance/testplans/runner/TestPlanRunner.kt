package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.http.Verifier2Interface
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.runner.req.ExpectedModuleOutcome
import id.walt.openid4vp.conformance.testplans.runner.req.TestPlanConfiguration
import id.walt.openid4vp.conformance.utils.JsonUtils.lenientJson
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

class TestPlanRunner(
    val config: TestPlanConfiguration,
    http: HttpClient,
    val conformanceHost: String,
    val conformancePort: Int,
    val testPlanName: String = "unknown"
) {
    companion object {
        val baseUrlBuilderSetup: URLBuilder.(host: String, port: Int) -> Unit = { cHost, cPort ->
            protocol = URLProtocol.HTTPS
            host = cHost
            port = cPort
        }
    }

    private val conformanceHttp = HttpClient {
        followRedirects = false

        defaultRequest {
            url {
                baseUrlBuilderSetup(conformanceHost, conformancePort)
            }
        }
        install(ContentNegotiation) {
            // Tolerate response fields added by newer conformance-suite releases
            json(lenientJson)
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    val conformance = ConformanceInterface(conformanceHost, conformancePort)
    val verifier2 = Verifier2Interface(http)

    /**
     * Create the test plan and run every module it contains.
     *
     * A plan expands into many modules (suite 5.2.2 yields 4 for `iso_mdl`/`plain_vp` and 11 for
     * `sd_jwt_vc`/HAIP), most of them negative tests. Modules are run in sequence and each one
     * produces its own [TestPlanResult]; a failing module does not stop the others, so a single
     * run reports the whole matrix.
     */
    suspend fun test(): List<TestPlanResult> {
        println("-- Conformane -- -> Setup")

        // Create test plan
        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig(
            config.testPlanCreationUrl
        )

        println("Creating test plan... ($createTestPlanUrl)")
        val createTestPlanResponse = conformance.createTestPlan(createTestPlanUrl, config.testPlanCreationConfiguration)

        val testPlanId = createTestPlanResponse.id
        val modules = createTestPlanResponse.modules
        println("Created test plan: $testPlanId with ${modules.size} module(s)")

        return modules.mapIndexed { index, module ->
            println()
            println("-- Module ${index + 1}/${modules.size}: ${module.testModule}")
            runCatching { runModule(testPlanId, module) }.getOrElse { error ->
                println("Module ${module.testModule} failed: ${error.message}")
                TestPlanResult(
                    testName = "$testPlanName / ${module.testModule}",
                    conformanceTestId = "N/A",
                    conformanceResult = "ERROR",
                    errorMessage = error.message,
                    expected = expectationFor(module.testModule),
                )
            }
        }
    }

    /**
     * Expectation for [testModule], preferring what the test plan declares in
     * [TestPlanConfiguration.moduleOutcomes] and falling back to the suite's module naming
     * convention for modules the plan does not mention.
     */
    private fun expectationFor(testModule: String): ExpectedModuleOutcome {
        val declared = config.moduleOutcomes[testModule]
        if (declared == null) {
            println("NOTICE: module '$testModule' is not declared in moduleOutcomes - inferring from its name")
        }
        return ExpectedModuleOutcome.forOutcome(
            declared ?: ExpectedModuleOutcome.undeclaredOutcomeFor(testModule)
        )
    }

    /** Run a single test module of the already created plan [testPlanId]. */
    private suspend fun runModule(
        testPlanId: String,
        module: CreateTestPlanResponse.Module,
    ): TestPlanResult {
        val expected = expectationFor(module.testModule)
        println("Expecting suite result in ${expected.acceptedConformanceResults}, verifier to ${expected.verifier}")

        // Create test - pass the variant from the module definition
        val createTestUrl = conformance.buildCreateTestUrl(testPlanId, module.testModule, module.variant)
        println("Creating test... ($createTestUrl)")
        val createTestResponse = conformance.createTest(createTestUrl)
        println()

        val testId = createTestResponse.id
        println("Created test: $testId")

        println("View test run at: https://$conformanceHost:$conformancePort/log-detail.html?log=${testId}")

        println("Checking if test is already ready for presentation")
        conformance.waitForTestStatus(testId, shouldBeWaiting = true)

        // Initial test run result
        val testRunResult = conformance.getTestRun(testId)
        val authorizationEndpointToUse = testRunResult.getExposedAuthorizationEndpoint().replace("http://", "https://")

        println("Use authorization endpoint: $authorizationEndpointToUse")

        println("-- Verifier 2 -- -> Creating verification session...")

        val verificationSessionResponse = verifier2.createVerificationSession(authorizationEndpointToUse, config.verificationSessionSetup)
        val verificationSessionId = verificationSessionResponse.sessionId
        println("Created Verification Session: $verificationSessionResponse")

        println("-- Conformance & Verifier 2 -- -> Present to Verifier2")

        // Present
        val requestUrl = if (config.presentUsingFullRequestUrl) {
            verificationSessionResponse.fullAuthorizationRequestUrl
                ?: error("Verifier2 returned no full authorization request URL, required for url_query")
        } else {
            verificationSessionResponse.bootstrapAuthorizationRequestUrl
                ?: error("Verifier2 returned no bootstrap authorization request URL")
        }
        conformanceHttp.get(requestUrl)

        // After presentation. Positive modules park in WAITING until the verification-result
        // screenshot placeholder is filled, so fill it while polling.
        println("Waiting until Conformance processing is done...")
        conformance.waitForTestStatus(testId, shouldBeWaiting = false, fulfillImagePlaceholders = true)

        val testRunInfo = conformance.getTestRunInfo(testId)
        println("Conformance: status=${testRunInfo.status} result=${testRunInfo.result}")

        val verifier2Info = verifier2.getVerificationSessionInfo(verificationSessionId)
        println("Verifier2: status=${verifier2Info.status} reason=${verifier2Info.statusReason}")

        return TestPlanResult(
            testName = "$testPlanName / ${module.testModule}",
            conformanceTestId = testId,
            conformanceStatus = testRunInfo.status,
            conformanceResult = testRunInfo.result,
            verifierStatus = verifier2Info.status,
            expected = expected,
        )
    }


}
