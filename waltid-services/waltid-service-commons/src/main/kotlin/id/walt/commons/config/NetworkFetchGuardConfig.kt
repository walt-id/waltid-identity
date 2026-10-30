package id.walt.commons.config

import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings

/**
 * Opt-outs for the private-network guard on this service's outbound requests: session webhooks and every
 * guarded HTTP client, such as DID resolution, verification policies, and wallet issuance and presentation
 * fetches. The settings are process-wide, so an opt-in applies to all of them.
 *
 * Defaults refuse loopback and private networks. Link-local and cloud-metadata addresses cannot be opted into.
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

/**
 * Applies the loaded `network-fetch-guard.conf`. Services call this from `init`, before serving, and list
 * [id.walt.commons.featureflag.CommonsFeatureCatalog.networkFetchGuardFeature] in their base features.
 */
fun applyNetworkFetchGuardConfig() = ConfigManager.getConfig<NetworkFetchGuardConfig>().apply()
