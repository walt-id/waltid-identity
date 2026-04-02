package id.walt.webdatafetching.engines

import io.ktor.client.*

expect object Apache5Engine : WebDataFetcherHttpEngine {
    override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
}
