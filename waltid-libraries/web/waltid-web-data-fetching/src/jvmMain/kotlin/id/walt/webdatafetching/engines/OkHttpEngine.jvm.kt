package id.walt.webdatafetching.engines

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

actual object OkHttpEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient() = HttpClient(OkHttp)
}
