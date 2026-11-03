package id.walt.webwallet.utils

import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.ktor.client.*

object WalletHttpClients {

    /**
     * Wallet fetches of caller-supplied URLs use the shared [WebDataFetchingConfiguration] defaults:
     * connect and request timeouts, a response-size cap, no redirects, and no retries. Logging stays
     * at headers with credentials redacted, not the full body.
     */
    var defaultConfiguration = WebDataFetchingConfiguration()

    var defaultMethod = {
        WebDataFetcher(WebDataFetcherId.CORE_WALLET_HTTP_CLIENT, defaultConfiguration)
    }

    fun getFetcher(): WebDataFetcher = defaultMethod()

    fun getHttpClient(): HttpClient = getFetcher().httpClient

}
