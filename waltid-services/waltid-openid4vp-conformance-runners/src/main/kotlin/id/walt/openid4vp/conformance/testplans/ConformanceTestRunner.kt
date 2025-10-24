package id.walt.openid4vp.conformance.testplans

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.MdlX509SanDnsRequestUriSignedDirectPost
import id.walt.openid4vp.conformance.testplans.plans.SdJwtVcX509SanDnsRequestUriSignedDirectPost
import id.walt.openid4vp.conformance.testplans.plans.TestPlan
import id.walt.openid4vp.conformance.testplans.runner.TestPlanRunner
import id.walt.openid4vp.verifier.OSSVerifier2FeatureCatalog
import id.walt.openid4vp.verifier.OSSVerifier2ServiceConfig
import id.walt.openid4vp.verifier.verifierModule
import id.walt.verifier.openid.models.authorization.ClientMetadata
import io.ktor.server.application.*
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertNotNull

class ConformanceTestRunner(
    verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session",
    val conformanceHost: String = "localhost.emobix.co.uk",
    val conformancePort: Int = 8443
) {


    private val testPlans: List<TestPlan> = listOf(
        MdlX509SanDnsRequestUriSignedDirectPost(verifier2UrlPrefix, conformanceHost, conformancePort),
        SdJwtVcX509SanDnsRequestUriSignedDirectPost(verifier2UrlPrefix, conformanceHost, conformancePort)
    )


    fun run() {
        val localVerifierHost = "127.0.0.1"
        val localVerifierPort = 7003

        E2ETest(localVerifierHost, localVerifierPort, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = "NOT-CONFIGURED_verifier2",
                        clientMetadata = ClientMetadata(
                            clientName = "Verifier2",
                            logoUri = "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
                        ),
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

            testPlans.forEach { plan ->
                val planName = plan::class.simpleName ?: plan::class.jvmName

                test(planName) {
                    TestPlanRunner(plan.config, http, conformanceHost, conformancePort).test()
                }
            }
        }
    }
}

fun main() = ConformanceTestRunner().run()
