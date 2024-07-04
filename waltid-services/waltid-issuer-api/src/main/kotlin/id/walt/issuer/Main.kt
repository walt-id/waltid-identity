package id.walt.issuer

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.FeatureManager.whenFeature
import id.walt.commons.web.WebService
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.entra.entraIssuance
import id.walt.issuer.issuance.OidcApi.oidcApi
import id.walt.issuer.issuance.issuerApi
import id.walt.issuer.web.plugins.*
import io.ktor.server.application.*

suspend fun main(args: Array<String>) {
    ServiceMain(
        ServiceConfiguration("issuer"), ServiceInitialization(
            features = FeatureCatalog,
            init = {
                WaltidServices.minimalInit()
            },
            run = WebService(Application::issuerModule).run()
        )
    ).main(args)
}

fun Application.configurePlugins() {
    configureExternalAuth()
    configureHTTP()
    configureMonitoring()
    configureRouting()
}

fun Application.issuerModule(withPlugins: Boolean = true) {
    if (withPlugins) {
//        configureExternalAuth()
        configurePlugins()
    }
    oidcApi()
    issuerApi();

    { entraIssuance() } whenFeature FeatureCatalog.entra
}

