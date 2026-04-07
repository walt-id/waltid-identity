package id.walt.trust.service

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.web.WebService
import id.walt.trust.service.config.TrustRegistryConfig
import id.walt.trust.service.plugins.configureHTTP
import id.walt.trust.service.plugins.configureMonitoring
import id.walt.trust.service.plugins.configureRouting
import id.walt.trust.service.routes.trustRegistryRoutes
import io.ktor.server.application.*

suspend fun main(args: Array<String>) {
    ServiceMain(
        ServiceConfiguration("trust-registry"),
        ServiceInitialization(
            features = FeatureCatalog,
            init = {
                TrustRegistryConfig.init()
            },
            run = WebService(Application::trustRegistryModule).run()
        )
    ).main(args)
}

fun Application.configurePlugins() {
    configureHTTP()
    configureMonitoring()
    configureRouting()
}

fun Application.trustRegistryModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    trustRegistryRoutes()
}
