package id.walt.webdatafetching

import id.walt.webdatafetching.utils.UrlUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*

class WebDataFetcher<T : Any>(id: String) {

    //private val log = KotlinLogging.logger("WebDataFetcher[$id]")

    val dataFetcherConfiguration = WebDataFetcherManager.getConfigurationForId(id)
    val httpClient = HttpClient()

    val cache = dataFetcherConfiguration.cache?.buildCache<T>()

    suspend inline fun <reified Res : T> fetch(url: String): T = fetch<Res>(UrlUtils.parseUrl(url))

    suspend inline fun <reified Res : T> fetch(url: Url): T {
        val cacheId = url.toString()

        val cachedValue = cache?.get(cacheId)

        if (cachedValue != null) {
            return cachedValue
        }

        val requestConfig = dataFetcherConfiguration.request

        val httpResponse = httpClient.get(url) {
            requestConfig?.expectSuccess?.let { expectSuccess = it }
            requestConfig?.headers?.forEach { (key, value) -> header(key, value) }
        } // TODO: More graceful error handling

        val parsedResponse: T = httpResponse.body<Res>()

        cache?.put(cacheId, parsedResponse)

        return parsedResponse
    }

}
