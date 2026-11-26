package id.walt.webdatafetching.ssrf

import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.url.installUrlAllowList
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.http.Url

/** No-op on iOS - see the commonMain doc comment for why this guard is JVM/Android-only. */
actual fun HttpClientConfig<*>.installPrivateNetworkGuard() {
}

internal actual fun HttpClientConfig<*>.installPrivateNetworkGuard(checkUrl: (Url) -> Unit) = installUrlAllowList(checkUrl)

internal actual fun WebDataFetchingConfiguration.credentialHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    http.engineCreator().getHttpClient(configure)
