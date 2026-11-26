package id.walt.webdatafetching

import id.walt.webdatafetching.config.RequestConfiguration
import id.walt.webdatafetching.ssrf.credentialHttpClient
import id.walt.webdatafetching.ssrf.installPrivateNetworkGuard
import id.walt.webdatafetching.utils.UrlUtils
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

class WebDataFetcher private constructor(
    private val id: String,
    private val defaultConfiguration: WebDataFetchingConfiguration?,
    clientOverride: HttpClient?,
) : AutoCloseable {

    constructor(id: String, defaultConfiguration: WebDataFetchingConfiguration? = null) : this(
        id = id,
        defaultConfiguration = defaultConfiguration,
        clientOverride = null,
    )

    constructor(id: WebDataFetcherId, defaultConfiguration: WebDataFetchingConfiguration? = null) : this(
        id = id.name,
        defaultConfiguration = defaultConfiguration
    )

    val dataFetcherConfiguration = WebDataFetcherManager.getConfigurationForId(id, defaultConfiguration)

    val httpClient = clientOverride ?: buildHttpClient()

    private fun buildHttpClient(): HttpClient {
        val credentialFetch = id in credentialFetcherIds
        val configure: HttpClientConfig<*>.() -> Unit = {
            // Ktor wire logging includes the entire URL and redirect Location, even at HEADERS.
            val configuration = if (credentialFetch) {
                dataFetcherConfiguration.copy(logging = dataFetcherConfiguration.logging.copy(enable = false))
            } else {
                dataFetcherConfiguration
            }
            configuration.applyConfigurationToHttpClient(this)
            installPrivateNetworkGuard(::requireUrlAllowed)
            install(ContentNegotiation) { json() }
        }
        return if (credentialFetch) {
            dataFetcherConfiguration.credentialHttpClient(configure)
        } else {
            dataFetcherConfiguration.http.engineCreator().getHttpClient(configure)
        }
    }

    @PublishedApi
    internal val urlPolicyRevision: String get() = WebDataFetcherManager.urlPolicyRevision(id)

    @PublishedApi
    internal fun requireUrlAllowed(url: Url) {
        WebDataFetcherManager.getConfigurationForId(id, defaultConfiguration).url?.requireUrlAllowed(url)
    }

    companion object {
        private val credentialFetcherIds = setOf(
            WebDataFetcherId.STATUS_POLICY.name,
            WebDataFetcherId.STATUS_POLICY2.name,
            WebDataFetcherId.JWKS_KEY_PROVIDER.name,
        )

        /**
         * Build a [WebDataFetcher] backed by a caller-provided [HttpClient] instead of one created
         * from the configured engine. Use this only when you need engine-specific configuration the
         * [WebDataFetchingConfiguration] cannot express (e.g. a custom TLS trust manager in tests).
         * The caller is responsible for content negotiation and the engine's cache policy.
         * Clients carrying sensitive wallet data must disable automatic response caching;
         * on Darwin, configure the session with `URLCache = null`.
         */
        fun wrapping(
            client: HttpClient,
            id: String,
            defaultConfiguration: WebDataFetchingConfiguration? = null,
        ): WebDataFetcher = WebDataFetcher(id = id, defaultConfiguration = defaultConfiguration, clientOverride = client)
    }

    val cache = dataFetcherConfiguration.cache?.buildCache<WebDataFetchingResult<*>>()

    /**
     * Fetch data from a remote URL
     */
    suspend inline fun <reified Res : Any> fetch(urlString: String): WebDataFetchingResult<Res> = fetch<Res>(UrlUtils.parseUrl(urlString))

    /**
     * Fetch data from a remote URL
     */
    suspend inline fun <reified Res : Any> fetch(
        url: Url,
        useCache: Boolean = true,
        customRequestConfig: RequestConfiguration? = null,
        customRequest: HttpRequestBuilder.() -> Unit = {}
    ): WebDataFetchingResult<Res> {
        // Redirect destinations can have separate URL rules. Only reuse a response obtained
        // under the same URL policy revision, even when the original URL is still allowed.
        val cacheId = if (useCache && cache != null) {
            "$urlPolicyRevision#${customRequestConfig?.getCacheId()}#$url#${Res::class.simpleName}"
        } else ""

        requireUrlAllowed(url)

        val cachedValue = if (useCache) cache?.get(cacheId) else null

        if (cachedValue != null) {
            @Suppress("UNCHECKED_CAST")
            return cachedValue as WebDataFetchingResult<Res>
        }

        val defaultRequestConfig = dataFetcherConfiguration.request

        val httpResponseResult = runCatching {
            httpClient.request(url) {
                defaultRequestConfig?.applyConfiguration(this)
                customRequestConfig?.applyConfiguration(this)
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
            if (Res::class == String::class) {
                @Suppress("UNCHECKED_CAST")
                body as Res
            } else {
                runCatching {
                    dataFetcherConfiguration.decoding.json.decodeFromString<Res>(body)
                }.getOrElse { ex ->
                    throw DataFetchingException(
                        // The body is not included: the URL is often caller-supplied, and this message can reach
                        // client-facing errors and logs.
                        "Server answered request with non-/invalid JSON (to request to $url)",
                        cause = ex,
                    )
                }
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

        if (useCache) {
            cache?.put(cacheId, result.copy(cached = true))
        }

        return result
    }

    /**
     * Raw fetching: Does not use certain WebDataFetcher features
     * Does not use:
     * - Caching
     * - Response deserialization
     */
    suspend inline fun rawFetch(url: Url, customRequest: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        requireUrlAllowed(url)
        val requestConfig = dataFetcherConfiguration.request

        val httpResponse = httpClient.request(url) {
            requestConfig?.applyConfiguration(this)
            customRequest(this)
        }

        if (dataFetcherConfiguration.request?.expectSuccess == true) {
            if (!httpResponse.status.isSuccess()) {
                // TODO: More detailed error message
                throw DataFetchingException("Response to http request was not successful, but success was expected", null)
            }
        }

        return httpResponse
    }

    suspend inline fun rawFetch(urlString: String): HttpResponse = rawFetch(UrlUtils.parseUrl(urlString))

    suspend inline fun rawFetch(urlString: String, customRequest: HttpRequestBuilder.() -> Unit): HttpResponse =
        rawFetch(UrlUtils.parseUrl(urlString), customRequest)

    /**
     * Send data to a remote URL
     */
    suspend inline fun <reified Req : Any, reified Res : Any> send(url: Url, req: Req, customRequestConfig: RequestConfiguration? = null) = fetch<Res>(url, useCache = false, customRequestConfig = customRequestConfig) {
        if (dataFetcherConfiguration.request?.method == null) {
            method = HttpMethod.Post
        }

        if (req !is String) {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }
        setBody(req)
    }

    /**
     * Send data to a remote URL
     */
    suspend inline fun <reified Req : Any, reified Res : Any> send(urlString: String, req: Req, customRequestConfig: RequestConfiguration? = null) =
        send<Req, Res>(UrlUtils.parseUrl(urlString), req, customRequestConfig)

    suspend fun sendForm(url: String, parameters: Parameters, customRequest: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        requireUrlAllowed(UrlUtils.parseUrl(url))
        val requestConfig = dataFetcherConfiguration.request

        val httpResponse = httpClient.submitForm(url, parameters, false) {
            requestConfig?.applyConfiguration(this)
            customRequest(this)
        }

        if (dataFetcherConfiguration.request?.expectSuccess == true) {
            if (!httpResponse.status.isSuccess()) {
                // TODO: More detailed error message
                throw DataFetchingException("Response to http request was not successful, but success was expected", null)
            }
        }

        return httpResponse
    }

    override fun close() {
        httpClient.close()
    }

}
