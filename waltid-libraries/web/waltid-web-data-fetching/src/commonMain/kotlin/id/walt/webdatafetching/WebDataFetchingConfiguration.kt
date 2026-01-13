package id.walt.webdatafetching

import id.walt.webdatafetching.config.*
import id.walt.webdatafetching.config.url.UrlConfiguration
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable

@Serializable
data class WebDataFetchingConfiguration(
    val url: UrlConfiguration? = null,
    val cache: CacheConfiguration? = null,
    val timeouts: TimeoutConfiguration? = null,
    val retry: RetryConfiguration? = RetryConfiguration(),
    val request: RequestConfiguration? = null,
    val engine: RequestEngineConfiguration? = null,
    val decoding: ResponseBodyDecodingConfiguration = ResponseBodyDecodingConfiguration(),
    val logging: LoggingConfiguration = LoggingConfiguration()

    //val defaultOnError: JsonElement?,
) {
    companion object {
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


    fun applyConfigurationToHttpClient(http: HttpClientConfig<*>) {
        http.engine {
            engine?.pipelining?.let { this.pipelining = it }
            engine?.proxy?.let { this.proxy = it.toKtorProxyConfig() }
        }
        engine?.followRedirects?.let { http.followRedirects = it }

        http.install(ContentNegotiation) {
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
                format = logging.format
                level = logging.level
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
