import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.credentials.verification.PolicyManager
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.issuerModule
import id.walt.verifier.policies.PresentationDefinitionPolicy
import id.walt.verifier.verifierModule
import id.walt.commons.web.plugins.configureSerialization
import id.walt.commons.web.plugins.configureStatusPages
import id.walt.webwallet.db.Db
import id.walt.webwallet.webWalletModule
import id.walt.webwallet.webWalletSetup
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import id.walt.issuer.FeatureCatalog as IssuerFeatureCatalog
import id.walt.verifier.FeatureCatalog as VerifierFeatureCatalog
import id.walt.webwallet.FeatureCatalog as WalletFeatureCatalog

class E2ETest {

    data class TestWebService(
        val module: Application.() -> Unit,
    ) {
        private val webServiceModule: Application.() -> Unit = {
            configureStatusPages()
            configureSerialization()

            module.invoke(this)
        }

        fun run(block: ApplicationTestBuilder.() -> Unit): suspend () -> Unit = {
            testApplication {
                application {
                    webServiceModule()

                    block.invoke(this@testApplication)
                }
            }
        }
    }

    suspend fun tests(block: ApplicationTestBuilder.() -> Unit) {
        ServiceMain(
            ServiceConfiguration("e2e"), ServiceInitialization(
                features = listOf(IssuerFeatureCatalog, VerifierFeatureCatalog, WalletFeatureCatalog),
                init = {
                    webWalletSetup()
                    PolicyManager.registerPolicies(PresentationDefinitionPolicy())
                    WaltidServices.minimalInit()
                    Db.start()
                },
                run = TestWebService(Application::e2eTestModule).run(block)
            )
        ).main(emptyArray())
    }

    @Test
    fun e2e() = runTest(timeout = 5.minutes) {
        tests {
            val client = createClient {
                /*install(ContentNegotiation) {
                    json()
                }*/
            }

            // the e2e http request tests here
        }
    }
}

private fun Application.e2eTestModule() {
    webWalletModule(true)
    issuerModule(false)
    verifierModule(false)
}
