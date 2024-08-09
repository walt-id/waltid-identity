package id.walt.webwallet

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager.whenFeature
import id.walt.commons.web.WebService
import id.walt.crypto.keys.oci.WaltCryptoOci
import id.walt.did.helpers.WaltidServices
import id.walt.webwallet.db.Db
import id.walt.webwallet.db.Migration
import id.walt.webwallet.web.Administration.configureAdministration
import id.walt.webwallet.web.controllers.*
import id.walt.webwallet.web.controllers.NotificationController.notifications
import id.walt.webwallet.web.controllers.PushController.push
import id.walt.webwallet.web.plugins.*
import id.walt.webwallet.web.plugins.walletOpenApiPluginAmendment
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

private val log = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    ServiceMain(
        ServiceConfiguration("wallet"), ServiceInitialization(
            features = FeatureCatalog,
            featureAmendments = mapOf(
                CommonsFeatureCatalog.openApiFeature to walletOpenApiPluginAmendment,
                CommonsFeatureCatalog.authenticationServiceFeature to walletAuthenticationPluginAmendment
            ),
            init = {
                webWalletSetup()
                WaltidServices.minimalInit()
                WaltCryptoOci.init()
                Db.start()
                Migration.Keys.execute()
            },
            run = WebService(Application::webWalletModule).run()
        )
    ).main(args)
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
}

fun Application.webWalletModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    auth()
    accounts();
    { push() } whenFeature FeatureCatalog.pushFeature

    // Wallet routes
    keys()
    dids()
    credentials()
    exchange()
    history();
    { web3accounts() } whenFeature FeatureCatalog.web3
    issuers()
    eventLogs()
    manifest()
    categories()
    reports()
    settings();
    { reasons() } whenFeature FeatureCatalog.rejectionReasonsFeature
    {
        silentExchange()
        notifications()
        trustRegistry()
    } whenFeature FeatureCatalog.silentExchange

    // DID Web Registry
    { didRegistry() } whenFeature FeatureCatalog.didWebRegistry

    // utility APIs
    utility()
}
