package id.walt.webwallet.utils

import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.ktor.client.*

object WalletHttpClients {

    /**
     * Same limits as the shared web-data client: timeouts, a response-size cap, no redirects, and no
     * retries. Header logging redacts credentials instead of recording the response body.
     */
    var defaultConfiguration = WebDataFetchingConfiguration()

    var defaultMethod = {
        WebDataFetcher(WebDataFetcherId.WALLET_API_HTTP_CLIENT, defaultConfiguration)
    }

    fun getHttpClient(): HttpClient = defaultMethod().httpClient

}
