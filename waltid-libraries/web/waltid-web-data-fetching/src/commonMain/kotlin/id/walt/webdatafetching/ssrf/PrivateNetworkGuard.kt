package id.walt.webdatafetching.ssrf

import io.ktor.client.HttpClientConfig

/** Thrown when a request targets a blocked address. Callers should not echo [message] verbatim to untrusted clients. */
class BlockedAddressException(message: String) : Exception(message)

/**
 * Deployment-level opt-outs for [installPrivateNetworkGuard], read live on every guarded request rather than
 * captured once at client-construction time - a host app sets these once at startup (from its own config), before
 * any guarded `HttpClient` is built.
 *
 * Link-local addresses (which includes the 169.254.169.254 cloud-metadata address), multicast, and wildcard
 * targets have no legitimate use for a server-side fetch and stay blocked unconditionally - there is no flag for
 * them. Loopback and RFC1918/IPv6-ULA private ranges default to blocked too, but many real deployments run their
 * issuer/verifier/wallet services as siblings on the same host or the same private network, where a wallet
 * legitimately needs to reach its own org's issuer at a private address. Set the matching flag to `true` for a
 * deployment where that's expected, rather than disabling the guard entirely.
 */
object PrivateNetworkGuardSettings {
    var allowLoopback: Boolean = false
    var allowPrivateNetworks: Boolean = false
}

/**
 * Installs a guard that resolves the target host to its actual IP address(es) before connecting, and refuses
 * blocked targets - checked against the resolved address, not the hostname string, so a DNS name that merely
 * *looks* public cannot be used to reach an internal address. See [PrivateNetworkGuardSettings] for exactly what's
 * blocked and what's configurable. Re-validates on every redirect hop it follows, since the initial URL passing
 * validation says nothing about where a 3xx response then points.
 *
 * This exists for server-side HTTP clients that fetch caller-supplied URLs (issuer metadata, `request_uri`,
 * `credential_offer_uri`, token/credential endpoints, ...) - i.e. clients where an SSRF turns "the server made a
 * request" into "the attacker made a request as the server, from inside the network." It is not meaningful for a
 * client acting only on behalf of the end user running it (a wallet app talking to its own device), which is why
 * only the JVM/Android actual performs a real check; other platforms install a no-op.
 */
expect fun HttpClientConfig<*>.installPrivateNetworkGuard()
