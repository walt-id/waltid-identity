package id.walt.webwallet

import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.WebConfig
import id.walt.webwallet.db.Db
import id.walt.webwallet.web.Administration.configureAdministration
import id.walt.webwallet.web.controllers.*
import id.walt.webwallet.web.controllers.NotificationController.notifications
import id.walt.webwallet.web.controllers.PushController.push
import id.walt.webwallet.web.plugins.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

private val log = KotlinLogging.logger { }

fun main(args: Array<String>) {
    log.info { "Starting walt.id wallet..." }
    
    log.debug { "Running in path: ${Path(".").absolutePathString()}" }
    
    webWalletSetup()
    
    log.info { "Reading configurations..." }
    ConfigManager.loadConfigs(args)
    
    Db.start()
    
    val webConfig = ConfigManager.getConfig<WebConfig>()
    log.info { "Starting web server (binding to ${webConfig.webHost}, listening on port ${webConfig.webPort})..." }
    embeddedServer(
        CIO,
        port = webConfig.webPort,
        host = webConfig.webHost,
        module = Application::webWalletModule
    ).start(wait = true)
}

fun webWalletSetup() {
    log.info { "Setting up..." }
    Security.addProvider(BouncyCastleProvider())
    runCatching { Db.dataDirectoryPath.createDirectories() }
}

private fun Application.configurePlugins() {
    configureSecurity()
    configureHTTP()
    configureMonitoring()
    configureStatusPages()
    configureSerialization()
    configureAdministration()
    configureRouting()
    configureOpenApi()
}


fun Application.webWalletModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    
    auth()
    push()
    notifications()
    
    // Wallet routes
    accounts()
    keys()
    dids()
    credentials()
    exchange()
    history()
    web3accounts()
    nfts()
    issuers()
    eventLogs()
    manifest()
    categories()
    reports()
    settings()
    reasons()
    trustRegistry()
    silentExchange()

    // DID Web Registry
    didRegistry()
}
