package id.walt.webdatafetching.engines

import io.ktor.client.*

interface WebDataFetcherHttpEngine {

    fun getHttpClient(block: HttpClientConfig<*>.() -> Unit = {}): HttpClient

}
