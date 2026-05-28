package id.walt.webdatafetching.engines

import io.ktor.client.*

actual object CIOEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
        throw UnsupportedOperationException("CIO engine is not available in Windows, only Java/Android")
    }
}
