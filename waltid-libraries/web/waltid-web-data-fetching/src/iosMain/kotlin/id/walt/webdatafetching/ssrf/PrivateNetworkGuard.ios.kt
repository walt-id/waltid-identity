package id.walt.webdatafetching.ssrf

import io.ktor.client.HttpClientConfig

/** No-op on iOS - see the commonMain doc comment for why this guard is JVM/Android-only. */
actual fun HttpClientConfig<*>.installPrivateNetworkGuard() {
}
