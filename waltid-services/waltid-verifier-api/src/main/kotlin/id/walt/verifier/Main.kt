package id.walt.verifier

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.FeatureManager.whenFeature
import id.walt.commons.web.WebService
import id.walt.credentials.verification.PolicyManager
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.verifier.entra.entraVerifierApi
import id.walt.verifier.lspPotential.lspPotentialVerificationTestApi
import id.walt.verifier.policies.PresentationDefinitionPolicy
import id.walt.verifier.web.plugins.configureHTTP
import id.walt.verifier.web.plugins.configureMonitoring
import id.walt.verifier.web.plugins.configureRouting
import io.ktor.server.application.*

suspend fun main(args: Array<String>) {
    ServiceMain(
        ServiceConfiguration("verifier"), ServiceInitialization(
            features = FeatureCatalog,
            init = {
                //WaltidServices.init()
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }

                PolicyManager.registerPolicies(PresentationDefinitionPolicy())
            },
            run = WebService(Application::verifierModule).run()
        )
    ).main(args)
}

fun Application.configurePlugins() {
    configureHTTP()
    configureMonitoring()
    configureRouting()
}

fun Application.verifierModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    verfierApi();
    { entraVerifierApi() } whenFeature FeatureCatalog.entra
    { lspPotentialVerificationTestApi() } whenFeature FeatureCatalog.lspPotential
}
