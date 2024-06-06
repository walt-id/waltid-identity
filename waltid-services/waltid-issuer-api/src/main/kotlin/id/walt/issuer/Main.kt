package id.walt.issuer

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.did.helpers.WaltidServices
import id.walt.commons.featureflag.FeatureManager.whenFeature
import id.walt.issuer.entra.entraIssuance
import id.walt.issuer.issuance.OidcApi.oidcApi
import id.walt.issuer.issuance.issuerApi
import id.walt.issuer.web.plugins.*
import id.walt.commons.web.WebService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*

private val log = KotlinLogging.logger { }

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
    configureHTTP()
    configureMonitoring()
    configureRouting()
    configureOpenApi()
}

fun Application.issuerModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    oidcApi()
    issuerApi();

    { entraIssuance() } whenFeature FeatureCatalog.entra
}

