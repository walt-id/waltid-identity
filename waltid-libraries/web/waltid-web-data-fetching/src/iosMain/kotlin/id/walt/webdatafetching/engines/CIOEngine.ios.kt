package id.walt.webdatafetching.engines

import io.ktor.client.*

// CIO is intentionally NOT available on iOS/Android: with multiple engines on the classpath,
// Ktor's default engine auto-discovery can resolve to CIO, whose pure-Kotlin TLS path throws
// "TLS sessions are not supported on Native platform" on Kotlin/Native. Use the Native engine
// (Darwin on iOS) instead.
actual object CIOEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
        throw UnsupportedOperationException("CIO engine is not available on iOS; use the Native (Darwin) engine instead")
    }
}
