package id.walt.webdatafetching.engines

import io.ktor.client.*
import io.ktor.client.engine.java.*

actual object NativeEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient() = HttpClient(Java)
}
