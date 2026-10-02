package id.walt.webdatafetching.ssrf

import io.ktor.client.HttpClientConfig

/**
 * No-op: the browser/Node network stack already refuses cross-origin/private-network access on the caller's
 * behalf where it matters (a wallet in the browser fetching on behalf of its own user, not a multi-tenant server
 * fetching on behalf of others). See the commonMain doc comment for the actual threat model this guards against.
 */
actual fun HttpClientConfig<*>.installPrivateNetworkGuard() {
}
