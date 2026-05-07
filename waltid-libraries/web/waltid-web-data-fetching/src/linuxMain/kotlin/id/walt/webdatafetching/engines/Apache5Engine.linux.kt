package id.walt.webdatafetching.engines

import io.ktor.client.*

actual object Apache5Engine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
        throw UnsupportedOperationException("Apache5 engine is not available in Linux, only JVM")
    }
}
