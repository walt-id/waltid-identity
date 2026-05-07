package id.walt.webdatafetching.engines

import io.ktor.client.*

actual object JavaEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
        throw UnsupportedOperationException("Java engine is not available in JavaScript, only JVM")
    }
}
