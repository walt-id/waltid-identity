package id.walt.webwallet

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager.whenFeature
import id.walt.commons.web.WebService
import id.walt.crypto.keys.aws.WaltCryptoAws
import id.walt.crypto.keys.azure.WaltCryptoAzure
import id.walt.crypto.keys.oci.WaltCryptoOci
import id.walt.did.dids.DidService
import id.walt.webwallet.db.Db
import id.walt.webwallet.web.Administration.configureAdministration
import id.walt.webwallet.web.controllers.*
import id.walt.webwallet.web.controllers.NotificationController.notifications
import id.walt.webwallet.web.controllers.PushController.push
import id.walt.webwallet.web.controllers.auth.defaultAuthRoutes
import id.walt.webwallet.web.controllers.auth.keycloak.keycloakAuthRoutes
import id.walt.webwallet.web.controllers.auth.ktorAuthnzFrontendRoutes
import id.walt.webwallet.web.controllers.auth.ktorAuthnzRoutes
import id.walt.webwallet.web.controllers.auth.oidc.oidcAuthRoutes
import id.walt.webwallet.web.controllers.auth.x5c.x5cAuthRoutes
import id.walt.webwallet.web.controllers.exchange.exchange
import id.walt.webwallet.web.controllers.exchange.exchangeExternalSignatures
import id.walt.webwallet.web.plugins.*
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
                DidService.minimalInit()
                WaltCryptoOci.init()
                WaltCryptoAws.init()
                WaltCryptoAzure.init()
                Db.start()
            },
            run = WebService(Application::webWalletModule).run()
        )
    ).main(args)
}

fun webWalletSetup() {
    log.info { "Setting up wallet ..." }

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

    // Auth
    {
        defaultAuthRoutes()
        keycloakAuthRoutes();
        { oidcAuthRoutes() } whenFeature FeatureCatalog.oidcAuthenticationFeature
        { x5cAuthRoutes() } whenFeature FeatureCatalog.x5cAuthFeature
    } whenFeature FeatureCatalog.legacyAuthenticationFeature

    {
        ktorAuthnzRoutes()
        ktorAuthnzFrontendRoutes()
    } whenFeature FeatureCatalog.ktorAuthnzAuthenticationFeature


    accounts();

    { push() } whenFeature FeatureCatalog.pushFeature

    // Wallet routes
    keys()
    dids()
    credentials()
    exchange();
    { exchangeExternalSignatures() } whenFeature FeatureCatalog.externalSignatureEndpointsFeature
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
