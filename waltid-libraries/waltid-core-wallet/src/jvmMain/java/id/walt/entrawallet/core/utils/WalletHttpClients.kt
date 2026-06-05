package id.walt.webwallet.utils

import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.webdatafetching.config.LoggingConfiguration
import id.walt.webdatafetching.config.RequestEngineConfiguration
import io.ktor.client.*
import io.ktor.client.plugins.logging.*

object WalletHttpClients {

    /**
     * Default [WebDataFetcher] configuration for wallet HTTP clients.
     *
     * Routes client creation through [WebDataFetcher] so the engine (default: Native — Java on
     * JVM, with TLS 1.3) and request/logging configuration are managed centrally instead of
     * constructing a raw [HttpClient] with the platform default engine.
     *
     * Preserves the previous behaviour: redirects are NOT followed and request/response logging
     * is enabled at [LogLevel.ALL].
     */
    var defaultConfiguration = WebDataFetchingConfiguration(
        engine = RequestEngineConfiguration(followRedirects = false),
        logging = LoggingConfiguration(enable = true, level = LogLevel.ALL),
    )

    var defaultMethod = {
        WebDataFetcher(WebDataFetcherId.CORE_WALLET_HTTP_CLIENT, defaultConfiguration)
    }

    fun getFetcher(): WebDataFetcher = defaultMethod()

    fun getHttpClient(): HttpClient = getFetcher().httpClient

}
