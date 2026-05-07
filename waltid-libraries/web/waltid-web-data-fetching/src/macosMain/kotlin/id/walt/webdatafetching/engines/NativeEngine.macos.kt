package id.walt.webdatafetching.engines

import io.ktor.client.*
import io.ktor.client.engine.darwin.*

actual object NativeEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin, block)
}
