package id.walt.wallet2

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.WebService
import id.walt.did.dids.DidService
import id.walt.wallet2.auth.configureWallet2Auth
import id.walt.wallet2.auth.registerWallet2AuthRoutes
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
    ServiceMain(
        ServiceConfiguration("wallet", version = BuildConfig.VERSION),
        ServiceInitialization(
            features = OSSWallet2FeatureCatalog,
            init = {
                DidService.minimalInit()
            },
            run = WebService(Application::wallet2Module).run()
        )
    ).main(args)
}

fun Application.wallet2Module(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    if (FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)) {
        configureWallet2Auth()
    }
    wallet2Api()
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

fun Application.wallet2Api() {
    routing {
        if (FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.authFeature)) {
            registerWallet2AuthRoutes()
        }
        OSSWallet2Service.run { registerRoutes() }
    }
}
