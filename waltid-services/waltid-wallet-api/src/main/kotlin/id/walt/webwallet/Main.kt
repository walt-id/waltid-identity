package id.walt.webwallet

import id.walt.ServiceConfiguration
import id.walt.ServiceInitialization
import id.walt.ServiceMain
import id.walt.crypto.keys.oci.WaltCryptoOci
import id.walt.did.helpers.WaltidServices
import id.walt.web.WebService
import id.walt.webwallet.db.Db
import id.walt.webwallet.web.Administration.configureAdministration
import id.walt.webwallet.web.controllers.*
import id.walt.webwallet.web.controllers.NotificationController.notifications
import id.walt.webwallet.web.controllers.PushController.push
import id.walt.webwallet.web.plugins.configureHTTP
import id.walt.webwallet.web.plugins.configureMonitoring
import id.walt.webwallet.web.plugins.configureOpenApi
import id.walt.webwallet.web.plugins.configureRouting
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

private val log = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    ServiceMain(ServiceConfiguration("wallet"), ServiceInitialization(
        features = FeatureCatalog,
        init = {
            webWalletSetup()
            WaltidServices.minimalInit()
            WaltCryptoOci.init()
            Db.start()
        },
        run =  WebService(Application::webWalletModule).run()
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
    configureAdministration()
    configureRouting()
    configureOpenApi()
}


fun Application.webWalletModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
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
