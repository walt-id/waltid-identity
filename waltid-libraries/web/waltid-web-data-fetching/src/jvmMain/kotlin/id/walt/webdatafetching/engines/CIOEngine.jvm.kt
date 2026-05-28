package id.walt.webdatafetching.engines

import io.ktor.client.*
import io.ktor.client.engine.cio.*

actual object CIOEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(CIO, block)
}
