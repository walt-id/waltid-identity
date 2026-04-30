package id.walt.webdatafetching.engines

// Jetty is currently not an interesting choice (exclusively http2)
// and was temporarily removed to reduce binary size.

/*
import io.ktor.client.*


expect object JettyEngine : WebDataFetcherHttpEngine {
    override fun getHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
}
*/
