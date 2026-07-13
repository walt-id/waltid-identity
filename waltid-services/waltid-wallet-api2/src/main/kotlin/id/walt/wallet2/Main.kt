package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.WebService
import id.walt.did.dids.DidService
import id.walt.wallet2.auth.configureWallet2Auth
import id.walt.wallet2.auth.registerWallet2AuthRoutes
import id.walt.wallet2.config.UrlHopliteDecoder
import id.walt.wallet2.persistence.ExposedWalletStore
import id.walt.wallet2.persistence.Wallet2PersistenceConfig
import id.walt.wallet2.persistence.initWallet2Database
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.slf4j.event.Level

suspend fun main(args: Array<String>) {
    // Register custom decoder for Url before config loading
    ConfigManager.registerCustomDecoder(UrlHopliteDecoder())

    ServiceMain(
        ServiceConfiguration("wallet", version = BuildConfig.VERSION),
        ServiceInitialization(
            features = OSSWallet2FeatureCatalog,
            init = {
                DidService.minimalInit()
                // If persistence is enabled, swap in the Exposed-backed wallet store
                if (FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.persistenceFeature)) {
                    val config = ConfigManager.getConfig<Wallet2PersistenceConfig>()
                    val db = initWallet2Database(config)
                    OSSWallet2Service.walletStore = ExposedWalletStore(db)
                }
            },
            run = WebService {
                val authConfig = if (FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)) {
                    configureWallet2Auth()
                } else null
                wallet2Module(withPlugins = true, authConfig = authConfig)
            }.run()
        )
    ).main(args)
}

/**
 * Ktor Application module. [authConfig] is pre-resolved by the caller (Main or test)
 * so this function can remain non-suspend and compatible with [E2ETest.testBlock].
 */
fun Application.wallet2Module(withPlugins: Boolean = true, authConfig: OSSWallet2AuthConfig? = null) {
    if (withPlugins) {
        configurePlugins()
    }
    wallet2Api(authConfig)
}

fun Application.configurePlugins() {
    configureHTTP()
    configureMonitoring()
}

fun Application.configureHTTP() {
    install(Compression)
    install(CORS) {
        allowHeaders { true }
        allowMethod(HttpMethod.Options)
        allowNonSimpleContentTypes = true
        allowCredentials = true
        allowOrigins { true }
    }
    install(ForwardedHeaders)
    install(XForwardedHeaders)
}

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
        callIdMdc("call-id")
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String -> callId.isNotEmpty() }
    }
}

fun Application.wallet2Api(authConfig: OSSWallet2AuthConfig? = null) {
    routing {
        if (FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)) {
            requireNotNull(authConfig) { "No auth config is provided for auth feature!" }
            registerWallet2AuthRoutes(tokenExpiry = authConfig.tokenExpiry)
        }
        OSSWallet2Service.run { registerRoutes() }
    }
}
