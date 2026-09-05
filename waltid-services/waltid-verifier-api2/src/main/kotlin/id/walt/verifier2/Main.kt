package id.walt.verifier2

import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.config.ConfigManager
import id.walt.commons.web.WebService
import id.walt.credentials.trustedauthorities.DcqlTrustedAuthoritiesChecker
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.verifier2.config.ClientMetadataHopliteDecoder
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler
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
import kotlin.uuid.Uuid

suspend fun main(args: Array<String>) {
    // Register custom decoder for ClientMetadata before config loading
    ConfigManager.registerCustomDecoder(ClientMetadataHopliteDecoder())

    ServiceMain(
        ServiceConfiguration("verifier", version = BuildConfig.VERSION), ServiceInitialization(
            features = OSSVerifier2FeatureCatalog,
            init = {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
                // Wire trusted_authorities checker per OID4VP §6.1.1 (AKI-based trust chain)
                Verifier2VPDirectPostHandler.trustedAuthoritiesChecker = DcqlTrustedAuthoritiesChecker.checker
                OSSVerifier2Manager.initialize()
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

    // Enable CRL Distribution Point support for certificate revocation checking
    // This allows the PKIX validator to fetch CRLs from the CRLDistributionPoint
    // extension in X.509 certificates when enableRevocation is true in vICAL policies
    System.setProperty("com.sun.security.enableCRLDP", "true")

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
        // Without a generator, a request that arrives without X-Request-ID has no call id at all.
        // Handlers that require one - the OpenID4VCI endpoints served by issuer-api2, which this
        // monitoring setup is also reused by - then reject the request with 400 "Missing call ID"
        // before the request is ever processed. Public OAuth clients do not send the header.
        generate { Uuid.random().toString() }
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
