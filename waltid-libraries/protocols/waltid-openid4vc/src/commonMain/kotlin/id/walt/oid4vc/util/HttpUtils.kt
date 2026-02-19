package id.walt.oid4vc.util

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

val http = HttpClient {

    // For CI/CD OkHttp client should be used. CIO client seems to have some issues with timeouts when connecting to
    // localhost in CI/CD pipelines,
    // which can lead to hanging tests. OkHttp is more robust in these environments.
    install(ContentNegotiation) {
        json()
    }

    install(HttpTimeout) {
        // For CI/CD a low maxEndpointIdleTime seems to be needed.

        // default seems to be 5s
        connectTimeoutMillis = 500
    }
    /*install(ContentEncoding) {
        deflate(1.0F)
        gzip(0.9F)
    }*/
}
