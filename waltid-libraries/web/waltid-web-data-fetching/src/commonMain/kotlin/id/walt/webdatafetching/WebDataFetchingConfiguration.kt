package id.walt.webdatafetching

import id.walt.webdatafetching.config.*
import id.walt.webdatafetching.config.url.UrlConfiguration
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

/**
 * Defaults apply to every [WebDataFetcher] that does not set its own configuration, including VCT
 * integrity, VICAL, and Wallet2 issuance and presentation. Redirects and retries are off. A deployment
 * that needs either sets [engine] or [retry] for that fetcher.
 */
@Serializable
data class WebDataFetchingConfiguration(
    val http: HttpEngine = HttpEngine.Native,
    val url: UrlConfiguration? = null,
    val cache: CacheConfiguration? = null,
    val timeouts: TimeoutConfiguration? = DEFAULT_TIMEOUTS,
    val retry: RetryConfiguration? = null,
    /** A non-positive value disables the cap. */
    val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    val request: RequestConfiguration? = null,
    val engine: RequestEngineConfiguration? = null,
    val decoding: ResponseBodyDecodingConfiguration = ResponseBodyDecodingConfiguration(),
    val logging: LoggingConfiguration = LoggingConfiguration()

    //val defaultOnError: JsonElement?,
) {
    /**
     * Merge [override] on top of this configuration (the base).
     *
     * Precedence: override > this > (implicit defaults)
     *
     * - Nullable fields: override value is used when non-null, otherwise this value is kept.
     * - Non-nullable fields ([http], [decoding], [logging], [maxResponseBytes]): override value is used only when it
     *   differs from the class-level default, so a layer that was never explicitly set does not
     *   silently replace a lower-layer customisation.
     */
    fun mergeWith(override: WebDataFetchingConfiguration): WebDataFetchingConfiguration = WebDataFetchingConfiguration(
        http      = if (override.http != Default.http) override.http else http,
        url       = override.url ?: url,
        cache     = override.cache ?: cache,
        timeouts  = override.timeouts ?: timeouts,
        retry     = override.retry ?: retry,
        maxResponseBytes = if (override.maxResponseBytes != Default.maxResponseBytes) override.maxResponseBytes else maxResponseBytes,
        request   = override.request ?: request,
        engine    = override.engine ?: engine,
        decoding  = if (override.decoding != Default.decoding) override.decoding else decoding,
        logging   = if (override.logging != Default.logging) override.logging else logging,
    )

    companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 10L * 1024 * 1024

        val DEFAULT_TIMEOUTS = TimeoutConfiguration(
            connectTimeout = 10.seconds,
            requestTimeout = 30.seconds,
            socketTimeout = 30.seconds,
        )

        val Default = WebDataFetchingConfiguration()

        val Example = WebDataFetchingConfiguration(
            url = UrlConfiguration.Example,
            cache = CacheConfiguration.Example,
            timeouts = TimeoutConfiguration.Example,
            retry = RetryConfiguration.Example,
            request = RequestConfiguration.Example,
            engine = RequestEngineConfiguration.Example,
            decoding = ResponseBodyDecodingConfiguration.Example,
            logging = LoggingConfiguration.Example
        )
    }


    fun applyConfigurationToHttpClient(
        http: HttpClientConfig<*>,
        httpLogger: Logger = Logger.DEFAULT,
    ) {
        http.engine {
            engine?.pipelining?.let { this.pipelining = it }
            engine?.proxy?.let { this.proxy = it.toKtorProxyConfig() }
        }
        http.followRedirects = engine?.followRedirects ?: false

        http.install(ContentNegotiation) {
            // Protocol requests own their Accept values; keep JSON as the implicit default.
            acceptHeaderMergeStrategy = ContentTypeMergeStrategy.SkipIfPresent
            json(json = decoding.json)
        }
        timeouts?.run {
            http.install(HttpTimeout) {
                connectTimeout?.let { connectTimeoutMillis = it.inWholeMilliseconds }
                requestTimeout?.let { requestTimeoutMillis = it.inWholeMilliseconds }
                socketTimeout?.let { socketTimeoutMillis = it.inWholeMilliseconds }
            }
        }
        if (logging.enable) {
            http.install(Logging) {
                logger = httpLogger
                format = logging.format
                level = logging.level
                sanitizeHeader { name -> headerIsSanitized(name, logging.sanitizedHeaders) }
            }
        }
        if (maxResponseBytes > 0) {
            http.install(ResponseSizeLimit) {
                maxBytes = maxResponseBytes
            }
        }
        //http.install(io.ktor.client.plugins.compression.ContentEncoding)

        request?.userAgent.let {
            when (it) {
                is RequestConfiguration.UserAgentConfiguration.BrowserUserAgent -> http.BrowserUserAgent()
                is RequestConfiguration.UserAgentConfiguration.CurlUserAgent -> http.CurlUserAgent()
                is RequestConfiguration.UserAgentConfiguration.CustomUserAgent, null -> {
                    http.install(UserAgent) {
                        agent = it?.agent ?: "walt.id WebDataFetcher"
                    }
                }
            }
        }


        retry?.configureHttpClient(http)
    }


}
