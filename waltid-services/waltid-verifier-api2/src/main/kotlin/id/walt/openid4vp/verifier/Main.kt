package id.walt.openid4vp.verifier

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.web.WebService
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
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
    ServiceMain(
        ServiceConfiguration("verifier"), ServiceInitialization(
            features = OSSVerifier2FeatureCatalog,
            init = {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            run = WebService(Application::verifierModule).run()
        )
    ).main(args)
}

fun Application.configurePlugins() {
    configureHTTP()
    configureMonitoring()
}

fun Application.verifierModule(withPlugins: Boolean = true) {
    if (withPlugins) {
        configurePlugins()
    }
    verifierApi();
    //{ entraVerifierApi() } whenFeature FeatureCatalog.entra
}

fun Application.configureHTTP() {
    install(Compression)
    install(CORS) {

        // TODO: Restrict CORS settings in production.
        allowHeaders { true }
        allowMethod(HttpMethod.Options)
        allowNonSimpleContentTypes = true
        allowCredentials = true
        allowOrigins { true }

    }
    install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
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


fun Application.verifierApi() {
    routing {
        Verifier2Service.run { registerRoute() }
    }
}
