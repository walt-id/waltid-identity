package id.walt.commons.config

/**
 * Opt-outs for the private-network guard on outbound webhooks. Defaults refuse loopback and private networks.
 * Link-local and cloud-metadata addresses cannot be opted into.
 */
data class NetworkFetchGuardConfig(
    val allowLoopback: Boolean = false,
    val allowPrivateNetworks: Boolean = false,
    val trustedHostSuffixes: List<String> = emptyList(),
) : WaltConfig()
