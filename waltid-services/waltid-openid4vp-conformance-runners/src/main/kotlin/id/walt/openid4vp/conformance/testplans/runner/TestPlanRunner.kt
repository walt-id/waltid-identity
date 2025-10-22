package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.http.Verifier2Interface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.runner.req.TestPlanConfiguration
import id.walt.openid4vp.verifier.Verification2Session.VerificationSessionStatus
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.assertEquals

class TestPlanRunner(
    val config: TestPlanConfiguration,
    val http: HttpClient
) {
    companion object {

        val baseUrlBuilderSetup: URLBuilder.() -> Unit = {
            protocol = URLProtocol.HTTPS
            host = "localhost.emobix.co.uk"
            port = 8443
        }

        private val conformanceHttp = HttpClient(OkHttp) {
            followRedirects = false

            defaultRequest {
                url {
                    baseUrlBuilderSetup()
                }
            }
            install(ContentNegotiation) {
                json()
            }
            install(Logging) {
                level = LogLevel.ALL
            }
        }
    }

    val conformance = ConformanceInterface()
    val verifier2 = Verifier2Interface(http)

    suspend fun test(): TestPlanResult {
        println("-- Conformane -- -> Setup")

        // Create test plan
        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig(
            config.testPlanCreationUrl
        )

        println("Creating test plan... ($createTestPlanUrl)")
        val createTestPlanResponse = conformance.createTestPlan(createTestPlanUrl, config.testPlanCreationConfiguration)

        if (createTestPlanResponse.modules.size > 1) {
            println("NOTICE: Suddenly, there is more than one test module available!")
        }

        val testPlanId = createTestPlanResponse.id
        val testModule = createTestPlanResponse.modules.first().testModule
        println("Created test plan: $testPlanId")

        // Create test
        val createTestUrl = conformance.buildCreateTestUrl(testPlanId, testModule)
        println("Creating test... ($createTestUrl)")
        val createTestResponse = conformance.createTest(createTestUrl)
        println()

        val testId = createTestResponse.id
        println("Created test: $testId")

        println("View test run at: https://localhost.emobix.co.uk:8443/log-detail.html?log=${testId}")

        // Initial test run result
        val testRunResult = conformance.getTestRun(testId)
        val authorizationEndpointToUse = testRunResult.getExposedAuthorizationEndpoint()

        println("Use authorization endpoint: $authorizationEndpointToUse")

        println("-- Verifier 2 -- -> Creating verification session...")

        val verificationSessionResponse = verifier2.createVerificationSession(authorizationEndpointToUse, config.verificationSessionSetup)
        val verificationSessionId = verificationSessionResponse.sessionId
        println("Created Verification Session: $verificationSessionResponse")


        println("Checking if test is already ready for presentation")

        conformance.waitForTestStatus(testId, shouldBeWaiting = true)


        println("-- Conformance & Verifier 2 -- -> Present to Verifier2")

        // Present
        val bootstrapRequestUrl = verificationSessionResponse.bootstrapAuthorizationRequestUrl!!
        conformanceHttp.get(bootstrapRequestUrl) {

        }

        // After presentation
        conformance.waitForTestStatus(testId, shouldBeWaiting = false)

        val testRunInfo = conformance.getTestRunInfo(testId)
        println("Test run result2: $testRunInfo")

        val verifier2Info = verifier2.getVerificationSessionInfo(verificationSessionId)
        println("Verifier2 info: $verifier2Info")

        assertEquals("FINISHED", testRunInfo.status)
        assertEquals("PASSED", testRunInfo.result)
        assertEquals(VerificationSessionStatus.SUCCESSFUL, verifier2Info.status)

        return TestPlanResult(
            conformanceTestId = testId,
            conformanceStatus = testRunInfo.status,
            conformanceResult = testRunInfo.result,
            verifierStatus = verifier2Info.status,
        )
    }


}
