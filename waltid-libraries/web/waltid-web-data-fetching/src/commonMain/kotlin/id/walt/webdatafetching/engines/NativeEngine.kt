package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient

expect object NativeEngine: WebDataFetcherHttpEngine {
    override fun getHttpClient(): HttpClient
}
