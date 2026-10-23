package id.walt.ktornotifications

import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings

/**
 * Applies the deployment's private-network policy to every guarded outbound client in this process,
 * including [WebhookNotifier]. Read on each request, so this must run at startup before the first webhook.
 *
 * Link-local, multicast, wildcard and cloud-metadata addresses stay blocked. Loopback and private
 * networks are blocked unless the corresponding flag is set. [trustedHostSuffixes] skips only the
 * loopback and private-network checks for that hostname and its subdomains.
 */
fun applyNetworkFetchGuard(
    allowLoopback: Boolean,
    allowPrivateNetworks: Boolean,
    trustedHostSuffixes: Set<String>,
) {
    PrivateNetworkGuardSettings.allowLoopback = allowLoopback
    PrivateNetworkGuardSettings.allowPrivateNetworks = allowPrivateNetworks
    PrivateNetworkGuardSettings.trustedHostSuffixes = trustedHostSuffixes
}

fun currentNetworkFetchGuard(): NetworkFetchGuardState = NetworkFetchGuardState(
    allowLoopback = PrivateNetworkGuardSettings.allowLoopback,
    allowPrivateNetworks = PrivateNetworkGuardSettings.allowPrivateNetworks,
    trustedHostSuffixes = PrivateNetworkGuardSettings.trustedHostSuffixes,
)

data class NetworkFetchGuardState(
    val allowLoopback: Boolean,
    val allowPrivateNetworks: Boolean,
    val trustedHostSuffixes: Set<String>,
)
