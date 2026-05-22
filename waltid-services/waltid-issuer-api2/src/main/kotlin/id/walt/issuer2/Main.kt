package id.walt.issuer2

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.web.WebService
import id.walt.crypto.keys.aws.WaltCryptoAws
import id.walt.crypto.keys.azure.WaltCryptoAzure
import id.walt.did.dids.DidService
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.web.plugins.configureHTTP
import id.walt.issuer2.web.plugins.configureMonitoring
import id.walt.issuer2.web.plugins.configureRouting
import id.walt.issuer2.web.plugins.issuer2AuthenticationPluginAmendment
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

suspend fun main(args: Array<String>) {
    ServiceMain(
        ServiceConfiguration("issuer2", version = BuildConfig.VERSION),
        ServiceInitialization(
            features = FeatureCatalog,
            featureAmendments = mapOf(
                CommonsFeatureCatalog.authenticationServiceFeature to issuer2AuthenticationPluginAmendment
            ),
            init = {
                DidService.minimalInit()
                WaltCryptoAws.init()
                WaltCryptoAzure.init()
            },
            run = WebService(Application::issuer2Module).run()
        )
    ).main(args)
}

fun Application.configurePlugins() {
    configureHTTP()
    configureMonitoring()
    configureRouting()
}

fun Application.issuer2Module(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }

    val module = Issuer2Module.load()
    routing {
        module.managementController.register(this)
        module.openId4VciController.register(this)
    }
}
