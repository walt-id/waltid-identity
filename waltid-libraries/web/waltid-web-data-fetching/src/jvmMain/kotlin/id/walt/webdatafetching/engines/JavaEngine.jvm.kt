package id.walt.webdatafetching.engines

import io.ktor.client.*
import io.ktor.client.engine.java.*

actual object JavaEngine : id.walt.webdatafetching.engines.WebDataFetcherHttpEngine {
    actual override fun getHttpClient() = HttpClient(Java)
}
