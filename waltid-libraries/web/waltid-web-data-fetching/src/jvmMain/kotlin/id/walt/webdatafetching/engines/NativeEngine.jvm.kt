package id.walt.webdatafetching.engines

import io.ktor.client.*

actual object NativeEngine : WebDataFetcherHttpEngine {
    // OkHttp, not the Java client: the private-network guard installs its Dns here, and OkHttp connects
    // to the addresses that Dns returns.
    actual override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = okHttpClient(block)
}
