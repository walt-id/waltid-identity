package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.http.IssuerInterface
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
    val conformancePort: Int,
    val issuerInterface: IssuerInterface? = null
) {
    val conformance = ConformanceInterface(conformanceHost, conformancePort)

    suspend fun test(): List<TestPlanResult> {
        println("-- Conformance OID4VCI Issuer Test -- -> Setup")

        // For pre-authorized code flow, create a credential offer first
        val credentialOfferUri = if (config.requiresPreAuthorizedOffer && issuerInterface != null) {
            println("Creating pre-authorized credential offer...")
            val offerResponse = issuerInterface.createCredentialOffer(config.credentialProfileId)
            println("Created credential offer: ${offerResponse.offerId}")
            println("Credential offer URI: ${offerResponse.credentialOffer}")
            offerResponse.credentialOffer
        } else {
            null
        }

        // Create test plan
        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig(
            config.testPlanCreationUrl
        )

        println("Creating issuer test plan... ($createTestPlanUrl)")
        val testPlanConfig = if (credentialOfferUri != null) {
            val configWithOffer = config.withCredentialOffer(credentialOfferUri)
            println("Test plan configuration with credential offer: $configWithOffer")
            configWithOffer
        } else {
            config.testPlanCreationConfiguration
        }
        val createTestPlanResponse = conformance.createTestPlan(createTestPlanUrl, testPlanConfig)

        if (createTestPlanResponse.modules.isEmpty()) {
            throw IllegalStateException("No test modules available for the specified variant. Check your configuration.")
        }

        val testPlanId = createTestPlanResponse.id
        println("Created test plan: $testPlanId")
        println("The conformance suite will call issuer: ${config.issuerUrl}")
        if (credentialOfferUri != null) {
            println("Using credential offer: $credentialOfferUri")
        }

        return createTestPlanResponse.modules.map { module ->
            val testModule = module.testModule
            val createTestUrl = conformance.buildCreateTestUrl(testPlanId, testModule)
            println("Creating test for module $testModule... ($createTestUrl)")
            val createTestResponse = conformance.createTest(createTestUrl)
            val testId = createTestResponse.id

            println("Created test: $testId")
            println("View test run at: https://$conformanceHost:$conformancePort/log-detail.html?log=$testId")
            println("Waiting for conformance suite to complete issuer test...")

            waitForIssuerTestCompletion(testId)

            val testRunInfo = conformance.getTestRunInfo(testId)
            println("Module $testModule finished with status=${testRunInfo.status}, result=${testRunInfo.result}")

            // For skippable modules, accept INTERRUPTED status (feature not supported by issuer)
            val acceptedStatuses = if (testModule in config.skippableModules) {
                setOf("FINISHED", "INTERRUPTED")
            } else {
                setOf("FINISHED")
            }

            require(testRunInfo.status in acceptedStatuses) {
                "Issuer test $testModule expected status in $acceptedStatuses, got ${testRunInfo.status}"
            }

            // For skippable modules that interrupted, accept FAILED (feature not implemented)
            val acceptedResults = if (testModule in config.skippableModules) {
                if (testRunInfo.status == "INTERRUPTED") {
                    setOf("PASSED", "SKIPPED", "FAILED")  // INTERRUPTED tests may have FAILED result
                } else {
                    setOf("PASSED", "SKIPPED")
                }
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

            // OAuth authorization_code tests will be stuck in WAITING status when they need
            // user interaction (browser login). These tests must be completed manually in the
            // conformance suite UI. For automated tests, use pre-authorized_code grant type.
            if (counter > 60) {
                if (testRunInfo.status == "WAITING") {
                    throw IllegalStateException(
                        "Test $testId is stuck in WAITING status after ${counter - 1} seconds. " +
                        "This typically means the test requires user interaction (OAuth login). " +
                        "Please complete the test manually at https://$conformanceHost:$conformancePort/test-info/$testId"
                    )
                }
                throw IllegalStateException("Waited for issuer test $testId for ${counter - 1} seconds, but it is still ${testRunInfo.status}")
            }

            kotlinx.coroutines.delay(1_000)
        }
    }
}
