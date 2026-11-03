package id.walt.oid4vc.util

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

internal actual fun oid4vcHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(block)
