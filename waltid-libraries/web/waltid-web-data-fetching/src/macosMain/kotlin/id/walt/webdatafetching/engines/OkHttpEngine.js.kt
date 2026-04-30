package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient

actual object OkHttpEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
        throw UnsupportedOperationException("OkHttp engine is not available in macOS, only Java/Android")
    }
}
