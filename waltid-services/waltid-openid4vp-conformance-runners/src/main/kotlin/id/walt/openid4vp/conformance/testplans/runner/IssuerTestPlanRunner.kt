package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.runner.req.IssuerTestPlanConfiguration
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
    val conformanceHost: String,
    val conformancePort: Int
) {
    val conformance = ConformanceInterface(conformanceHost, conformancePort)

    suspend fun test(): List<TestPlanResult> {
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

        val testPlanId = createTestPlanResponse.id
        println("Created test plan: $testPlanId")
        println("The conformance suite will call issuer: ${config.issuerUrl}")

        return createTestPlanResponse.modules.map { module ->
            val testModule = module.testModule
            val createTestUrl = conformance.buildCreateTestUrl(testPlanId, testModule, config.moduleVariant)
            println("Creating test for module $testModule... ($createTestUrl)")
            val createTestResponse = conformance.createTest(createTestUrl)
            val testId = createTestResponse.id

            println("Created test: $testId")
            println("View test run at: https://$conformanceHost:$conformancePort/log-detail.html?log=$testId")
            println("Waiting for conformance suite to complete issuer test...")

            waitForIssuerTestCompletion(testId)

            val testRunInfo = conformance.getTestRunInfo(testId)
            println("Module $testModule finished with status=${testRunInfo.status}, result=${testRunInfo.result}")

            assertEquals("FINISHED", testRunInfo.status, "Issuer test $testModule should finish")

            val acceptedResults = if (testModule in config.skippableModules) {
                setOf("PASSED", "SKIPPED")
            } else {
                setOf("PASSED")
            }

            require(testRunInfo.result in acceptedResults) {
                "Issuer test $testModule expected one of $acceptedResults, got ${testRunInfo.result}"
            }

            TestPlanResult(
                conformanceTestId = testId,
                conformanceStatus = testRunInfo.status,
                conformanceResult = testRunInfo.result,
                verifierStatus = null
            )
        }
    }

    private suspend fun waitForIssuerTestCompletion(testId: String) {
        var counter = 0
        while (true) {
            counter++
            val testRunInfo = conformance.getTestRunInfo(testId)
            println("Current conformance test status: ${testRunInfo.status}")

            if (testRunInfo.status in setOf("FINISHED", "INTERRUPTED")) {
                return
            }

            if (counter > 60) {
                throw IllegalStateException("Waited for issuer test $testId for ${counter - 1} seconds, but it is still ${testRunInfo.status}")
            }

            kotlinx.coroutines.delay(1_000)
        }
    }
}
