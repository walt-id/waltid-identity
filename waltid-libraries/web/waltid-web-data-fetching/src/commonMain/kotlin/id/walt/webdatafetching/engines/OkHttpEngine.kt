package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient

expect object OkHttpEngine: WebDataFetcherHttpEngine {
    override fun getHttpClient(): HttpClient
}
