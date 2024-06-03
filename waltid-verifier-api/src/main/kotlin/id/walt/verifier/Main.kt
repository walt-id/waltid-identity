package id.walt.verifier

import id.walt.ConfigurationsList
import id.walt.ServiceConfiguration
import id.walt.ServiceInitialization
import id.walt.ServiceMain
import id.walt.credentials.verification.PolicyManager
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.verifier.base.config.OIDCVerifierServiceConfig
import id.walt.verifier.base.web.plugins.*
import id.walt.verifier.entra.EntraConfig
import id.walt.verifier.entra.entraVerifierApi
import id.walt.verifier.policies.PresentationDefinitionPolicy
import id.walt.web.WebService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*

private val log = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    ServiceMain(
        ServiceConfiguration("verifier"), ServiceInitialization(
            configs = ConfigurationsList(
                mandatory = listOf(
                    "verifier-service" to OIDCVerifierServiceConfig::class,
                ),
                optional = listOf(
                    "entra" to EntraConfig::class
                )
            ),
            init = {
                //WaltidServices.init()
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }

                PolicyManager.registerPolicies(PresentationDefinitionPolicy())
            },
            run = WebService( Application::verifierModule).run()
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

fun Application.verifierModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    verfierApi()
    entraVerifierApi()
}
