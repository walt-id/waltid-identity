package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

actual object Apache5Engine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
        throw UnsupportedOperationException("Apache5 engine is not available in iOS, only JVM")
    }
}
