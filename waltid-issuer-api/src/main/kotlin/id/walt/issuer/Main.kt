package id.walt.issuer

import id.walt.ConfigurationsList
import id.walt.ServiceConfiguration
import id.walt.ServiceInitialization
import id.walt.ServiceMain
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.OidcApi.oidcApi
import id.walt.issuer.base.config.CredentialTypeConfig
import id.walt.issuer.base.config.OIDCIssuerServiceConfig
import id.walt.issuer.base.web.plugins.*
import id.walt.web.WebService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*

private val log = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    ServiceMain(
        ServiceConfiguration("issuer"), ServiceInitialization(
            configs = ConfigurationsList(
                mandatory = listOf(
                    "issuer-service" to OIDCIssuerServiceConfig::class,
                    "credential-issuer-metadata" to CredentialTypeConfig::class
                )
            ),
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
    configureStatusPages()
    configureSerialization()
    configureRouting()
    configureOpenApi()
}

fun Application.issuerModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    oidcApi()
    issuerApi()
    entraIssuance()
}
