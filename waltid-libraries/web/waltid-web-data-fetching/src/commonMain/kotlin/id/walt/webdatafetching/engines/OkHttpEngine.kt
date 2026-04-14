package id.walt.webdatafetching.engines

import io.ktor.client.*

expect object OkHttpEngine : WebDataFetcherHttpEngine {
    override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
}
