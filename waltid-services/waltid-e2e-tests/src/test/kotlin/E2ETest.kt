import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.credentials.verification.PolicyManager
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.issuerModule
import id.walt.verifier.policies.PresentationDefinitionPolicy
import id.walt.verifier.verifierModule
import id.walt.web.WebService
import id.walt.webwallet.db.Db
import id.walt.webwallet.webWalletModule
import id.walt.webwallet.webWalletSetup
import io.ktor.server.application.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import id.walt.issuer.FeatureCatalog as IssuerFeatureCatalog
import id.walt.verifier.FeatureCatalog as VerifierFeatureCatalog
import id.walt.webwallet.FeatureCatalog as WalletFeatureCatalog

class E2ETest {

    @Test
    fun e2e() = runTest(timeout = 5.minutes) {
        ServiceMain(
            ServiceConfiguration("e2e"), ServiceInitialization(
                features = listOf(IssuerFeatureCatalog, VerifierFeatureCatalog, WalletFeatureCatalog),
                init = {
                    webWalletSetup()
                    PolicyManager.registerPolicies(PresentationDefinitionPolicy())
                    WaltidServices.minimalInit()
                    Db.start()
                },
                run = WebService(Application::e2eTestModule).run()
            )
        ).main(emptyArray())
    }
}

private fun Application.e2eTestModule() {
    webWalletModule(true)
    issuerModule(false)
    verifierModule(false)
}
