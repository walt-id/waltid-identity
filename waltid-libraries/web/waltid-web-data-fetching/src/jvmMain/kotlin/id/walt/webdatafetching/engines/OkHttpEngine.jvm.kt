package id.walt.webdatafetching.engines

import io.ktor.client.*

actual object OkHttpEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = okHttpClient(block)
}
