package id.walt.webdatafetching

import id.walt.webdatafetching.utils.UrlUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

class WebDataFetcher(id: String, defaultConfiguration: WebDataFetchingConfiguration? = null) {

    constructor(id: WebDataFetcherId, defaultConfiguration: WebDataFetchingConfiguration? = null) : this(
        id = id.name,
        defaultConfiguration = defaultConfiguration
    )

    private val log = KotlinLogging.logger("WebDataFetcher[$id]")
    val dataFetcherConfiguration = WebDataFetcherManager.getConfigurationForId(id, defaultConfiguration)

    val httpClient = dataFetcherConfiguration.http.engineCreator().getHttpClient {
        dataFetcherConfiguration.applyConfigurationToHttpClient(this)

        install(ContentNegotiation) {
            json()
        }
    }

    val cache = dataFetcherConfiguration.cache?.buildCache<WebDataFetchingResult<*>>()

    suspend inline fun <reified Res : Any> fetch(urlString: String): WebDataFetchingResult<Res> = fetch<Res>(UrlUtils.parseUrl(urlString))

    suspend inline fun <reified Res : Any> fetch(url: Url, customRequest: HttpRequestBuilder.() -> Unit = {}): WebDataFetchingResult<Res> {
        val cacheId = url.toString()

        dataFetcherConfiguration.url?.requireUrlAllowed(cacheId)

        val cachedValue = cache?.get(cacheId)

        if (cachedValue != null) {
            @Suppress("UNCHECKED_CAST")
            return cachedValue as WebDataFetchingResult<Res>
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

        if (dataFetcherConfiguration.request?.expectSuccess == true) {
            if (!httpResponse.status.isSuccess()) {
                // TODO: More detailed error message
                throw DataFetchingException("Response to http request was not successful, but success was expected", null)
            }
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

        val result = WebDataFetchingResult(
            body = parsedResponse,
            success = httpResponse.status.isSuccess(),
            status = httpResponse.status.value
        )

        cache?.put(cacheId, result.copy(cached = true))

        return result
    }

    suspend inline fun <reified Req : Any, reified Res : Any> send(url: Url, req: Req) = fetch<Res>(url) {
        if (dataFetcherConfiguration.request?.method == null) {
            method = HttpMethod.Post
        }

        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(req)
    }


    suspend inline fun <reified Req : Any, reified Res : Any> send(urlString: String, req: Req) =
        send<Req, Res>(UrlUtils.parseUrl(urlString), req)

}
