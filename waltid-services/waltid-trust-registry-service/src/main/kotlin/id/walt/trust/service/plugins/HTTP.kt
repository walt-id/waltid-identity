package id.walt.trust.service.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*

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
