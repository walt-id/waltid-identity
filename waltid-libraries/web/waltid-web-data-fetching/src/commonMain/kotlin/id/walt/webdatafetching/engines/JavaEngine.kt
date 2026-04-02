package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient

expect object JavaEngine: WebDataFetcherHttpEngine {
    override fun getHttpClient(): HttpClient
}
