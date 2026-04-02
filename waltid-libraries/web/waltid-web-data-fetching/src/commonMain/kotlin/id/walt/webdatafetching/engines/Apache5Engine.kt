package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient

expect object Apache5Engine: WebDataFetcherHttpEngine {
    override fun getHttpClient(): HttpClient
}
