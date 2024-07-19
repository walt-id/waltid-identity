package extensions

import E2ETestWebService
import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.credentials.verification.PolicyManager
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.FeatureCatalog
import id.walt.issuer.issuerModule
import id.walt.verifier.policies.PresentationDefinitionPolicy
import id.walt.verifier.verifierModule
import id.walt.webwallet.db.Db
import id.walt.webwallet.webWalletModule
import id.walt.webwallet.webWalletSetup
import io.ktor.server.application.*
import org.junit.jupiter.api.extension.*

class ServicesExtension : BeforeAllCallback, ParameterResolver {

    override fun beforeAll(context: ExtensionContext?) {
        ServiceMain(
            ServiceConfiguration("e2e-test"), ServiceInitialization(
                features = listOf(FeatureCatalog, id.walt.verifier.FeatureCatalog, id.walt.webwallet.FeatureCatalog),
                init = {
                    webWalletSetup()
                    PolicyManager.registerPolicies(PresentationDefinitionPolicy())
                    WaltidServices.minimalInit()
                    Db.start()
                },
                run = E2ETestWebService.TestWebService(Application::e2eTestModule).run()
            )
        ).main(arrayOf("-l", "trace"))
    }


    override fun supportsParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?) =
        parameterContext?.let {
            it.isAnnotated(Login::class.java) && it.parameter.type == String.javaClass
        } ?: error("Unsupported parameter")

    override fun resolveParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Any {
        return "my-login"
    }
}

private fun Application.e2eTestModule() {
    webWalletModule(true)
    issuerModule(false)
    verifierModule(false)
}

annotation class Login