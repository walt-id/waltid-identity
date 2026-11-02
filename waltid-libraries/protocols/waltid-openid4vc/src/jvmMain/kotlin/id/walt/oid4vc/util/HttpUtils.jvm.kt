package id.walt.oid4vc.util

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun oid4vcHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp, block)
