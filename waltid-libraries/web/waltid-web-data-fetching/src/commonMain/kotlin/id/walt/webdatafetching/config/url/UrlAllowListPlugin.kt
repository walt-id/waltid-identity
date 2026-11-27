package id.walt.webdatafetching.config.url

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.Url

internal fun HttpClientConfig<*>.installUrlAllowList(checkUrl: (Url) -> Unit) {
    install(createClientPlugin("WebDataFetcherUrlAllowList") {
        on(Send) { request ->
            checkUrl(request.url.build())
            proceed(request)
        }
    })
}
