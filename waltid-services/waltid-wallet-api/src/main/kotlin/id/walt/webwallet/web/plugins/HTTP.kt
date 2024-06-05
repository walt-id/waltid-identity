package id.walt.webwallet.web.plugins

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
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowNonSimpleContentTypes = true
        allowCredentials = true

        allowOrigins { true }
        anyHost()
    }
    install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
//    install(XHttpMethodOverride)//nginx delete verb
}
