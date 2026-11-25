package id.walt.webdatafetching.config.url

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin

/**
 * Applies a [UrlConfiguration] to every request the client sends, not only to requests made through
 * `WebDataFetcher.fetch`/`rawFetch`/`sendForm`. Callers that use `WebDataFetcher.httpClient` directly
 * (status lists, JWKS, Wallet2 issuance, ...) are covered too.
 *
 * [urlConfiguration] is read on every request, so a configuration applied after the client was built still
 * applies. A null configuration allows every URL.
 *
 * Install this after [id.walt.webdatafetching.ssrf.installPrivateNetworkGuard]. Send interceptors installed later
 * run inside earlier ones, so each redirect hop the guard follows is checked here as well.
 * A refused URL throws [IllegalArgumentException] before the request is sent.
 */
internal fun HttpClientConfig<*>.installUrlAllowList(urlConfiguration: () -> UrlConfiguration?) {
    install(createClientPlugin("WebDataFetcherUrlAllowList") {
        on(Send) { request ->
            urlConfiguration()?.requireUrlAllowed(request.url.buildString())
            proceed(request)
        }
    })
}
