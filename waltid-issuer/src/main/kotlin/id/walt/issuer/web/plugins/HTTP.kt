package id.walt.issuer.web.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*

fun Application.configureHTTP() {
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }
    install(CORS) {
        allowHeaders { true }
        allowMethod(HttpMethod.Options)
        allowNonSimpleContentTypes = true
        allowCredentials = true

        /*allowHost("localhost:3000")
        allowHost("127.0.0.1:3000")
        allowHost("0.0.0.0:3000")
        allowHost("host.docker.internal:3000")*/
        allowOrigins { true }
    }
    install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
}
