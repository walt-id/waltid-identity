package id.walt.issuer2

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.config.ConfigManager
import id.walt.commons.web.WebService
import id.walt.crypto.keys.aws.WaltCryptoAws
import id.walt.crypto.keys.azure.WaltCryptoAzure
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.issuer2.config.CredentialProfilesConfigDecoder
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import org.slf4j.event.Level

suspend fun main(args: Array<String>) {
    ConfigManager.registerCustomDecoder(CredentialProfilesConfigDecoder())

    ServiceMain(
        ServiceConfiguration("issuer", version = BuildConfig.VERSION),
        ServiceInitialization(
            features = OSSIssuer2FeatureCatalog,
            init = {
                DidService.apply {
                    minimalInit()
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
                WaltCryptoAws.init()
                WaltCryptoAzure.init()
            },
            run = WebService(Application::issuerModule).run()
        )
    ).main(args)
}

fun Application.configurePlugins() {
    configureHTTP()
    configureMonitoring()
}

fun Application.issuerModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    issuerApi()
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
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }
    install(SSE)
}

fun Application.issuerApi() {
    routing {
        OSSIssuer2Service.run { registerRoutes() }
    }
}
