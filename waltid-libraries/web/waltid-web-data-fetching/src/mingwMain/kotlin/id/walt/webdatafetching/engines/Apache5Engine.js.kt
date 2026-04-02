package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient

actual object Apache5Engine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(): HttpClient {
        throw UnsupportedOperationException("Apache5 engine is not available in Windows, only Java")
    }
}
