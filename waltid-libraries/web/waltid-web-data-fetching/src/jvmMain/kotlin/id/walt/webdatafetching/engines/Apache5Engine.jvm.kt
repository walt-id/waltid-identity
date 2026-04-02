package id.walt.webdatafetching.engines

import io.ktor.client.*
import io.ktor.client.engine.apache5.*

actual object Apache5Engine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient() = HttpClient(Apache5)
}
