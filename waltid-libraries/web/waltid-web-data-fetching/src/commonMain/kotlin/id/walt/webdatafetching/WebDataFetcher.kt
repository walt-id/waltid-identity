package id.walt.webdatafetching

import id.walt.webdatafetching.utils.UrlUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class WebDataFetcher(id: String, defaultConfiguration: WebDataFetchingConfiguration? = null) {

    private val log = KotlinLogging.logger("WebDataFetcher[$id]")
    val dataFetcherConfiguration = WebDataFetcherManager.getConfigurationForId(id, defaultConfiguration)

    val httpClient = dataFetcherConfiguration.http.engineCreator().getHttpClient {
        dataFetcherConfiguration.applyConfigurationToHttpClient(this)
    }

    val cache = dataFetcherConfiguration.cache?.buildCache<Any>()

    suspend inline fun <reified Res : Any> fetch(urlString: String): Res = fetch<Res>(UrlUtils.parseUrl(urlString))

    suspend inline fun <reified Res : Any> fetch(url: Url, customRequest: HttpRequestBuilder.() -> Unit = {}): Res {
        val cacheId = url.toString()

        dataFetcherConfiguration.url?.requireUrlAllowed(cacheId)

        val cachedValue = cache?.get(cacheId)

        if (cachedValue != null) {
            return cachedValue as Res
        }

        val requestConfig = dataFetcherConfiguration.request

        val httpResponseResult = runCatching {
            httpClient.request(url) {
                requestConfig?.applyConfiguration(this)
                customRequest(this)
            }
        }

        val httpResponse = httpResponseResult.getOrElse { ex ->
            throw DataFetchingException("Could not send request to: $url (${ex.message ?: "unknown error"})", ex)
        }

        val parsedResponse: Res = if (httpResponse.contentType()?.match(ContentType.Text.Plain) == true) {
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

    suspend inline fun <reified Req : Any, reified Res : Any> send(url: Url, req: Req): Res {
        return fetch<Res>(url) {
            if (dataFetcherConfiguration.request?.method == null) {
                method = HttpMethod.Post
            }
            setBody(req)
        }
    }

    suspend inline fun <reified Req : Any, reified Res : Any> send(urlString: String, req: Req): Res =
        send(UrlUtils.parseUrl(urlString), req)

}
