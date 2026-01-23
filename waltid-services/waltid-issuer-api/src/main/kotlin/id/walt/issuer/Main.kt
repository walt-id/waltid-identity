package id.walt.issuer

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager.whenFeature
import id.walt.commons.web.WebService
import id.walt.crypto.keys.aws.WaltCryptoAws
import id.walt.crypto.keys.azure.WaltCryptoAzure
import id.walt.did.dids.DidService
import id.walt.issuer.entra.entraIssuance
import id.walt.issuer.issuance.OidcApi.oidcApi
import id.walt.issuer.issuance.issuerApi
import id.walt.issuer.web.controllers.onboarding.onboardingApi
import id.walt.issuer.web.plugins.configureHTTP
import id.walt.issuer.web.plugins.configureMonitoring
import id.walt.issuer.web.plugins.configureRouting
import id.walt.issuer.web.plugins.issuerAuthenticationPluginAmendment
import io.ktor.server.application.*

suspend fun main(args: Array<String>) {
    ServiceMain(
        ServiceConfiguration("issuer"), ServiceInitialization(
            features = FeatureCatalog,
            featureAmendments = mapOf(
                CommonsFeatureCatalog.authenticationServiceFeature to issuerAuthenticationPluginAmendment
            ),
            init = {
                DidService.minimalInit()
                WaltCryptoAws.init()
                WaltCryptoAzure.init()
            },
            run = WebService(Application::issuerModule).run()
        )
    ).main(args)
}


fun Application.configurePlugins() {
    configureHTTP()
    configureMonitoring()
    configureRouting()
}

fun Application.issuerModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }

    oidcApi()
    onboardingApi()
    issuerApi();

    { entraIssuance() } whenFeature FeatureCatalog.entra
}

