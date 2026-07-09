package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

actual object JavaEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
        throw UnsupportedOperationException("Java engine is not available in iOS, only JVM")
    }
}
