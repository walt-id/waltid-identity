@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.openid4vp.conformance.testplans

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.MdlX509SanDnsRequestUriSignedDirectPost
import id.walt.openid4vp.conformance.testplans.plans.SdJwtVcX509SanDnsRequestUriSignedDirectPostJwt
import id.walt.openid4vp.conformance.testplans.plans.TestPlan
import id.walt.openid4vp.conformance.testplans.runner.TestPlanRunner
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.verifierModule
import io.ktor.server.application.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertNotNull

/**
 * Runner for Verifier2 conformance tests.
 *
 * Starts an embedded Verifier2 server and runs test plans against
 * the OpenID Foundation conformance suite.
 *
 * @param verifierUrlPrefix URL prefix where the verifier is accessible from the conformance suite.
 *                          Must be a publicly accessible URL (e.g., ngrok tunnel).
 * @param conformanceHost Conformance suite hostname.
 * @param conformancePort Conformance suite HTTPS port.
 */
class ConformanceTestRunner(
    private val verifierUrlPrefix: String = ConformanceConfig.VERIFIER_URL_PREFIX_PLACEHOLDER,
    private val conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST,
    private val conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT
) {

    private val testPlans: List<TestPlan> = listOf(
        MdlX509SanDnsRequestUriSignedDirectPost(verifierUrlPrefix, conformanceHost, conformancePort),
        SdJwtVcX509SanDnsRequestUriSignedDirectPostJwt(verifierUrlPrefix, conformanceHost, conformancePort)
    )

    fun run() {
        val localVerifierHost = ConformanceConfig.VERIFIER_LOCAL_HOST
        val localVerifierPort = ConformanceConfig.VERIFIER_LOCAL_PORT

        E2ETest(localVerifierHost, localVerifierPort, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = "NOT-CONFIGURED_verifier2",
                        urlPrefix = "NOT-CONFIGURED_http://$localVerifierHost:$localVerifierPort/verification-session",
                        urlHost = "NOT-CONFIGURED_openid4vp://authorize"
                    )
                )
            },
            init = {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::verifierModule
        ) {
            val http = testHttpClient()

            val conformance = ConformanceInterface(conformanceHost, conformancePort)

            test("Check if conformance available") {
                val conformanceVersion = conformance.getServerVersion()
                assertNotNull(conformanceVersion)
                println("Conformance server version $conformanceVersion available!")

                conformanceVersion
            }

            testPlans.forEach { plan ->
                val planName = plan::class.simpleName ?: plan::class.jvmName

                test(planName) {
                    val result = TestPlanRunner(plan.config, http, conformanceHost, conformancePort).test()
                    println("Plan $planName completed: ${result.conformanceTestId}")
                    println("  conformance=${result.conformanceResult}, verifier=${result.verifierStatus}")
                    result
                }
            }
        }
    }
}

/**
 * Main entry point for running conformance tests standalone.
 */
fun main() {
    val ngrokUrl = System.getenv("VERIFIER_NGROK_URL")
    val verifierUrlPrefix = ngrokUrl?.let { "$it/verification-session" }
        ?: ConformanceConfig.VERIFIER_URL_PREFIX_PLACEHOLDER

    println("Starting Verifier2 Conformance Tests")
    println("Verifier URL: $verifierUrlPrefix")
    println()

    ConformanceTestRunner(verifierUrlPrefix).run()
}
