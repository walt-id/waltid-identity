package id.walt.webdatafetching.engines

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit

// OkHttp runs at most 5 calls per host and 64 in total by default, and queues the rest inside the client. A server
// wallet calls the same issuer or verifier for every request it serves, so that queue became the ceiling: in load
// test 41677792595 nine pods held 45 calls at once between them, and each throughput limit measured was 45 divided
// by the time a call took. The idle pool is raised with it, or most of those calls would open a new connection.
private const val MAX_REQUESTS = 1024
private const val MAX_REQUESTS_PER_HOST = 512
private const val MAX_IDLE_CONNECTIONS = 256

/** An OkHttp client whose concurrency is limited by the servers it calls, not by OkHttp's defaults. */
internal fun okHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            dispatcher(Dispatcher().apply {
                maxRequests = MAX_REQUESTS
                maxRequestsPerHost = MAX_REQUESTS_PER_HOST
            })
            connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, 5, TimeUnit.MINUTES))
        }
    }
    block()
}
