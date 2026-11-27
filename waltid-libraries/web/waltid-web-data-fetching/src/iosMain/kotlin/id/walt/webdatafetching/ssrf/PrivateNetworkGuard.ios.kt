package id.walt.webdatafetching.ssrf

import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.url.UrlConfiguration
import id.walt.webdatafetching.config.url.installUrlAllowList
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/** No-op on iOS - see the commonMain doc comment for why this guard is JVM/Android-only. */
actual fun HttpClientConfig<*>.installPrivateNetworkGuard() {
}

internal actual fun HttpClientConfig<*>.installPrivateNetworkGuard(credentialFetch: Boolean, urlPolicy: () -> UrlConfiguration?) =
    installUrlAllowList { url -> urlPolicy()?.requireUrlAllowed(url) }

internal actual fun WebDataFetchingConfiguration.credentialHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    http.engineCreator().getHttpClient(configure)
