package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient

interface WebDataFetcherHttpEngine {

    fun getHttpClient(): HttpClient

}
