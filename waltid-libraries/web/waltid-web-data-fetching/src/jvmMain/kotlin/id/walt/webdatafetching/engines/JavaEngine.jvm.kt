package id.walt.webdatafetching.engines

import io.ktor.client.*
import io.ktor.client.engine.java.*

actual object JavaEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Java, block)
}
