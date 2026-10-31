package id.walt.webdatafetching.engines

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

actual object NativeEngine : WebDataFetcherHttpEngine {
    // OkHttp, not the Java client: the private-network guard installs its Dns here, and OkHttp connects
    // to the addresses that Dns returns.
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp, block)
}
