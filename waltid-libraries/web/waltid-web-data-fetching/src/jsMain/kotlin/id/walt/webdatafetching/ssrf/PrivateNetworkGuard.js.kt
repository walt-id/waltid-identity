package id.walt.webdatafetching.ssrf

import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.url.UrlConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.JsClientEngineConfig
import io.ktor.http.Url

/** The resolved-address guard is implemented on JVM and Android. */
actual fun HttpClientConfig<*>.installPrivateNetworkGuard() {
}

internal actual fun HttpClientConfig<*>.installPrivateNetworkGuard(credentialFetch: Boolean, urlPolicy: () -> UrlConfiguration?) {
    engine {
        if (this is JsClientEngineConfig) {
            val send = fetch
            fetch = { url, options ->
                val policy = urlPolicy()
                if (policy == null && !credentialFetch) {
                    send(url, options)
                } else {
                    policy?.requireUrlAllowed(Url(url))
                    // Node exposes Location to Ktor. Browsers hide it on manual redirects.
                    requireNotNull(options).redirect = "manual"
                    send(url, options).then { response ->
                        check(response.status.toInt() != 0) { "Cannot validate an opaque HTTP response against the URL policy" }
                        response
                    }
                }
            }
        }
    }
}

internal actual fun WebDataFetchingConfiguration.credentialHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    http.engineCreator().getHttpClient(configure)
