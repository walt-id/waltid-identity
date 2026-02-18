package id.walt.oid4vc.util

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

val http = HttpClient {

    install( HttpTimeout) {
        // For CI/CD a low maxEndpointIdleTime seems to be needed. For the CIO client the time is calculated:
        // private val maxEndpointIdleTime: Long = 2 * config.endpoint.connectTimeout

        // default seems to be 5s
        connectTimeoutMillis = 250
    }
    install(ContentNegotiation) {
        json()
    }
    /*install(ContentEncoding) {
        deflate(1.0F)
        gzip(0.9F)
    }*/
}
