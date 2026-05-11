package id.walt.webdatafetching.engines

import io.ktor.client.*

actual object OkHttpEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
        throw UnsupportedOperationException("OkHttp engine is not available in Linux, only Java/Android")
    }
}
