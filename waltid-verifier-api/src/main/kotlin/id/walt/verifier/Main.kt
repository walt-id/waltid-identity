package id.walt.verifier

import id.walt.credentials.verification.PolicyManager
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.verifier.base.config.ConfigManager
import id.walt.verifier.base.config.WebConfig
import id.walt.verifier.base.web.plugins.*
import id.walt.verifier.entra.entraVerifierApi
import id.walt.verifier.policies.PresentationDefinitionPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

private val log = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    log.debug { "verfier CLI starting ..." }
    
    log.debug { "Init walt services..." }
    //WaltidServices.init()
    DidService.apply {
        registerResolver(LocalResolver())
        updateResolversForMethods()
    }
    PolicyManager.registerPolicies(PresentationDefinitionPolicy())
    
    //ServiceMatrix("service-matrix.properties")
    
    log.info { "Reading configurations..." }
    ConfigManager.loadConfigs(args)
    
    val webConfig = ConfigManager.getConfig<WebConfig>()
    
    log.info { "Starting web server (binding to ${webConfig.webHost}, listening on port ${webConfig.webPort})..." }
    embeddedServer(CIO, port = webConfig.webPort, host = webConfig.webHost, module = Application::verifierModule)
        .start(wait = true)
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
