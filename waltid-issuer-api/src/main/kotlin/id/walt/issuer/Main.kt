package id.walt.issuer

import id.walt.did.helpers.WaltidServices
import id.walt.issuer.OidcApi.oidcApi
import id.walt.issuer.base.config.ConfigManager
import id.walt.issuer.base.config.WebConfig
import id.walt.issuer.base.web.plugins.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.klogging.config.ANSI_CONSOLE
import io.klogging.config.loggingConfiguration
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.delay

private val log = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    loggingConfiguration { ANSI_CONSOLE() }

    log.info { "Starting walt.id issuer..." }

    log.debug { "Init walt services..." }
    WaltidServices.minimalInit()

    log.info { "Reading configurations..." }
    ConfigManager.loadConfigs(args)

    val webConfig = ConfigManager.getConfig<WebConfig>()
    log.info { "Starting web server (binding to ${webConfig.webHost}, listening on port ${webConfig.webPort})..." }
    embeddedServer(
        CIO,
        port = webConfig.webPort,
        host = webConfig.webHost,
        module = Application::issuerModule
    ).start(wait = true)

    delay(50)
    log.info { "Issuer stopped." }
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
