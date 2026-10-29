package id.walt.commons.config

import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings

/**
 * Opt-outs for the private-network guard on outbound webhooks. Defaults refuse loopback and private networks.
 * Link-local and cloud-metadata addresses cannot be opted into.
 *
 * A configured [allowLoopback] of false is binding and wins over `walt.ssrf.allowLoopback`.
 */
data class NetworkFetchGuardConfig(
    val allowLoopback: Boolean = false,
    val allowPrivateNetworks: Boolean = false,
    val trustedHostSuffixes: List<String> = emptyList(),
) : WaltConfig()

/** Copies this config onto [PrivateNetworkGuardSettings]. False is an explicit deny. */
fun NetworkFetchGuardConfig.apply() {
    PrivateNetworkGuardSettings.allowLoopback = allowLoopback
    PrivateNetworkGuardSettings.allowPrivateNetworks = allowPrivateNetworks
    PrivateNetworkGuardSettings.trustedHostSuffixes = trustedHostSuffixes.toSet()
}
