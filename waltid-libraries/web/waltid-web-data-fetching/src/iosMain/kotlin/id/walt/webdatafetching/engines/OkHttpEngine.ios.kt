package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

actual object OkHttpEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
        throw UnsupportedOperationException("OkHttp engine is not available in iOS, only Java/Android")
    }
}
