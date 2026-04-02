package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient

actual object OkHttpEngine : WebDataFetcherHttpEngine {
    actual override fun getHttpClient(): HttpClient {
        throw UnsupportedOperationException("OkHttp engine is not available in Linux, only Java/Android")
    }
}
