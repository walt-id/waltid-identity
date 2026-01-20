package id.walt.webdatafetching

import id.walt.webdatafetching.utils.UrlUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class WebDataFetcher<T : Any>(id: String) {

    private val log = KotlinLogging.logger("WebDataFetcher[$id]")

    val dataFetcherConfiguration = WebDataFetcherManager.getConfigurationForId(id)

    val httpClient = HttpClient() {
        dataFetcherConfiguration.applyConfigurationToHttpClient(this)
    }

    val cache = dataFetcherConfiguration.cache?.buildCache<T>()

    suspend inline fun <reified Res : T> fetch(url: String): T = fetch<Res>(UrlUtils.parseUrl(url))

    suspend inline fun <reified Res : T> fetch(url: Url): T {
        val cacheId = url.toString()

        dataFetcherConfiguration.url?.requireUrlAllowed(cacheId)

        val cachedValue = cache?.get(cacheId)

        if (cachedValue != null) {
            return cachedValue
        }

        val requestConfig = dataFetcherConfiguration.request

        val httpResponseResult = runCatching {
            httpClient.request(url) {
                requestConfig?.applyConfiguration(this)
            }
        }

        val httpResponse = httpResponseResult.getOrElse { ex ->
            throw DataFetchingException("Could not send request to: $url (${ex.message ?: "unknown error"})", ex)
        }

        val parsedResponse: T = if (httpResponse.contentType()?.match(ContentType.Text.Plain) == true) {
            val body = httpResponse.bodyAsText()
            runCatching {
                dataFetcherConfiguration.decoding.json.decodeFromString<Res>(body)
            }.getOrElse { ex ->
                throw DataFetchingException(
                    "Server answered request with non-/invalid JSON: $body (to request to $url)",
                    cause = ex
                )
            }
        } else {
            runCatching {
                httpResponse.body<Res>()
            }.getOrElse { ex -> throw DataFetchingException("Failed to deserialize response from: $url", ex) }
        }

        cache?.put(cacheId, parsedResponse)

        return parsedResponse
    }

}
