@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.openid4vp.conformance.testplans

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.openid4vp.conformance.report.ConformanceCiFlags
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vp.verifier.Oid4vpVerifierVariantPlan
import id.walt.openid4vp.conformance.testplans.plans.vp.verifier.VerifierVariantMatrix
import id.walt.openid4vp.conformance.testplans.runner.TestPlanRunner
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.verifierModule
import io.ktor.server.application.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ConformanceTestRunner(
    verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session",
    val conformanceHost: String = "localhost.emobix.co.uk",
    val conformancePort: Int = 8443,
    /**
     * Budget for the whole matrix. [E2ETest.testBlock] wraps the block in its own `runTest`, whose
     * default is 5 minutes - not enough now that one run executes every module of every variant.
     *
     * Raised from 45 minutes when the `redirect_uri` client-id prefix took the matrix from 10
     * variants to 14; negative modules additionally sit through a 30-second suite-side wait each.
     */
    val timeout: Duration = 90.minutes,
) {


    /** Every point of the OpenID4VP 1.0 verifier matrix. */
    private val testPlans: List<Oid4vpVerifierVariantPlan> =
        VerifierVariantMatrix.all().map {
            Oid4vpVerifierVariantPlan(it, verifier2UrlPrefix, conformanceHost, conformancePort)
        }


    fun run() {
        val localVerifierHost = "127.0.0.1"
        val localVerifierPort = 7003

        E2ETest(localVerifierHost, localVerifierPort, true).testBlock(
            timeout = timeout,
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
                println("✅ Conformance server version $conformanceVersion available!")

                conformanceVersion
            }

            val results = mutableListOf<TestPlanResult>()
            testPlans.forEach { plan ->
                val planName = plan.name
                println("\nRunning verifier plan: $planName")
                val planResults = runCatching {
                    TestPlanRunner(plan.config, http, conformanceHost, conformancePort, planName).test()
                }.getOrElse { error ->
                    println("Plan $planName failed: ${error.message}")
                    listOf(
                        TestPlanResult(
                            testName = planName,
                            conformanceTestId = "N/A",
                            conformanceResult = "ERROR",
                            errorMessage = error.message,
                        )
                    )
                }
                println("Plan $planName completed: ${planResults.count { it.passed }}/${planResults.size} module(s) passed")
                planResults.forEach {
                    println("  ${if (it.passed) "✅" else "❌"} ${it.testName}: conformance=${it.conformanceResult}, verifier=${it.verifierStatus}${it.message?.let { m -> " - $m" } ?: ""}")
                }
                results += planResults
            }

            ConformanceReportWriter.writeTestPlanResults(
                role = ConformanceReportWriter.Role.VP_VERIFIER,
                results = results,
                conformanceHost = conformanceHost,
                conformancePort = conformancePort,
                producer = PRODUCER,
            )
            ConformanceReportWriter.failIfNeededFromTestPlanResults(
                role = ConformanceReportWriter.Role.VP_VERIFIER,
                results = results,
                allowFailure = ConformanceCiFlags.allowFailure(),
            )
        }
    }

    companion object {
        /** Owns every entry of the OpenID4VP verifier matrix in the role report. */
        private const val PRODUCER = "vp-verifier-matrix"
    }
}


fun main() = ConformanceTestRunner().run()
