package id.walt.webdatafetching.engines

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

object CIOEngine: WebDataFetcherHttpEngine {
    override fun getHttpClient() = HttpClient(CIO)


}
