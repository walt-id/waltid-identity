package id.walt.webwallet

import id.walt.ConfigurationsList
import id.walt.ServiceConfiguration
import id.walt.ServiceInitialization
import id.walt.ServiceMain
import id.walt.config.ConfigManager
import id.walt.config.list.WebConfig
import id.walt.crypto.keys.oci.WaltCryptoOci
import id.walt.did.helpers.WaltidServices
import id.walt.webwallet.config.*
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

private val log = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    ServiceMain(ServiceConfiguration("wallet"), ServiceInitialization(
        configs = ConfigurationsList(
            mandatory = listOf(
                "db" to DatasourceJsonConfiguration::class,
                "logins" to LoginMethodsConfig::class,
                "auth" to AuthConfig::class
            ),
            optional = listOf(
                "oidc" to OidcConfiguration::class
                // TODO: add remaining
            )
        ),
        init = {
            log.info { "Reading configurations..." }
            ConfigManager.loadConfigs(args)
            webWalletSetup()
            WaltidServices.minimalInit()
            WaltCryptoOci.init()
            Db.start()
        },
        run = {
            val webConfig = ConfigManager.getConfig<WebConfig>()
            log.info { "Starting web server (binding to ${webConfig.webHost}, listening on port ${webConfig.webPort}) ..." }

            embeddedServer(
                CIO,
                port = webConfig.webPort,
                host = webConfig.webHost,
                module = Application::webWalletModule,
                configure = {
                    shutdownGracePeriod = 2000
                    shutdownTimeout = 3000
                }
            ).start(wait = true)
        }
    )).main(args)
}

fun webWalletSetup() {
    log.info { "Setting up..." }
    Security.addProvider(BouncyCastleProvider())
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
    health()

    auth()
    accounts()
    push()
    notifications()

    // Wallet routes
    keys()
    dids()
    credentials()
    exchange()
    history()
    web3accounts()
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
