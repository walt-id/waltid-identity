package id.walt.issuer

import id.walt.issuer.config.ConfigManager
import id.walt.issuer.config.WebConfig
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.web.OidcApi.oidcApi
import id.walt.issuer.web.issuerApi
import id.walt.issuer.web.plugins.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

private val log = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    log.debug { "issuer CLI starting ..." }

    log.debug { "Init walt services..." }
    WaltidServices.init()

    log.info { "Reading configurations..." }
    ConfigManager.loadConfigs(args)

    val webConfig = ConfigManager.getConfig<WebConfig>()
    log.info { "Starting web server (binding to ${webConfig.webHost}, listening on port ${webConfig.webPort})..." }
    embeddedServer(
        CIO,
        port = webConfig.webPort,
        host = webConfig.webHost,
        module = Application::module
    ).start(wait = true)
}

fun Application.configurePlugins() {
    configureHTTP()
    configureMonitoring()
    configureStatusPages()
    configureSerialization()
    configureRouting()
    configureOpenApi()
}

fun Application.module() {
    configurePlugins()
    oidcApi()
    issuerApi()
}
