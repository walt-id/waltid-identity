package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ReaderSelectedTransportProvider

/** ISO mdoc Wi-Fi Aware security policies implemented by this transport. */
public enum class WifiAwareSecurityPolicy {
    /** Mandatory NAN Cipher Suite NCS-SK-128. */
    NcsSk128,
}

/** Side-effect-free Wi-Fi Aware runtime result. No radio resource has been prepared. */
public sealed interface WifiAwareProximityAvailability {
    /**
     * The selected policy passed side-effect-free platform eligibility checks.
     * Characteristics and resources that Android exposes only after attach are rechecked before advertising.
     */
    public data object Available : WifiAwareProximityAvailability

    /** The selected policy cannot currently be prepared. */
    public data class Unavailable(
        /** Whether this wallet build contains an implementation for the current platform. */
        public val implemented: Boolean,
        /** Stable machine-readable reason suitable for SDK capability mapping. */
        public val code: String,
        /** Non-sensitive diagnostic description. */
        public val message: String,
    ) : WifiAwareProximityAvailability {
        init {
            require(code.isNotBlank()) { "A Wi-Fi Aware unavailability code must not be blank" }
            require(message.isNotBlank()) { "A Wi-Fi Aware unavailability message must not be blank" }
        }
    }
}

/** Immutable transaction inputs for one holder Wi-Fi Aware publisher. */
public data class WifiAwareProximityTransportConfiguration(
    /** Exact tagged EDeviceKeyBytes used for the transaction-derived service and passphrase. */
    public val eDeviceKeyBytes: ImmutableBytes,
    /** NAN data-path policy. */
    public val securityPolicy: WifiAwareSecurityPolicy = WifiAwareSecurityPolicy.NcsSk128,
) {
    init {
        require(eDeviceKeyBytes.size > 0) { "EDeviceKeyBytes must not be empty" }
    }
}

/** Platform Wi-Fi Aware preflight and provider factory. */
public interface WifiAwareProximityTransportFactory {
    /** Reports availability without attaching, publishing, allocating a socket, or prompting. */
    public suspend fun capability(
        securityPolicy: WifiAwareSecurityPolicy,
    ): WifiAwareProximityAvailability

    /** Creates a provider without preparing radio resources. */
    public fun create(
        configuration: WifiAwareProximityTransportConfiguration,
    ): ReaderSelectedTransportProvider
}
