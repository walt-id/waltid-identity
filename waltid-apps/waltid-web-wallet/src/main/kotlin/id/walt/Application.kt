package id.walt

import id.walt.config.ConfigManager
import id.walt.db.Db
import id.walt.web.Administration.configureAdministration
import id.walt.web.controllers.*
import id.walt.web.controllers.NotificationController.notifications
import id.walt.web.controllers.PushController.push
import id.walt.web.plugins.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private val log = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    log.info { "Starting walt.id wallet..." }

    log.debug { "Running in path: ${Path(".").absolutePathString()}" }

    log.info { "Setting up..." }
    Security.addProvider(BouncyCastleProvider())

    log.info { "Reading configurations..." }
    ConfigManager.loadConfigs(args)

    Db.start()

//    val webConfig = ConfigManager.getConfig<WebConfig>()
//    log.info { "Starting web server (binding to ${webConfig.webHost}, listening on port ${webConfig.webPort})..." }
//    embeddedServer(CIO, port = webConfig.webPort, host = webConfig.webHost, module = Application::module)
//        .start(wait = true)
    log.info { "Starting web server (binding to 0.0.0.0, listening on port 4545)..." }
    embeddedServer(CIO, port = 4545, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.configurePlugins() {
    configureSecurity()
    configureHTTP()
    configureMonitoring()
    configureStatusPages()
    configureSerialization()
    configureAdministration()
    configureRouting()
    configureOpenApi()
}


fun Application.module() {
    configurePlugins()
    auth()
    push()
    notifications()

    // Wallet routes
    credentials()
    dids()
    keys()
    exchange()
    history()
    web3accounts()
    accounts()
    nfts()
    issuers()
}
