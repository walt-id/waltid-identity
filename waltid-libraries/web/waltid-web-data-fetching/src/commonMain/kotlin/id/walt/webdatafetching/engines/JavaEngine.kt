package id.walt.webdatafetching.engines

import io.ktor.client.*

expect object JavaEngine : WebDataFetcherHttpEngine {
    override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
}
