package id.walt.webdatafetching.engines

import io.ktor.client.*

expect object NativeEngine : WebDataFetcherHttpEngine {
    override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
}
