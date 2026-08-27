package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityTransportProvider

/** BLE role set whose runtime prerequisites are evaluated before creating a session. */
public enum class BleMdocRoleSelection {
    CENTRAL_CLIENT,
    PERIPHERAL_SERVER,
    DUAL,
}

/** Side-effect-free BLE runtime result. Radio resources have not been prepared in either state. */
public sealed interface BleProximityAvailability {
    /** The selected BLE roles can be prepared on the current platform and runtime state. */
    public data object Available : BleProximityAvailability

    /** The selected BLE roles cannot currently be prepared. */
    public data class Unavailable(
        /** Stable machine-readable reason suitable for mapping at the Wallet SDK boundary. */
        public val code: String,
        /** Non-sensitive diagnostic description. Applications should map this to reviewed product copy. */
        public val message: String,
    ) : BleProximityAvailability {
        init {
            require(code.isNotBlank()) { "A BLE unavailability code must not be blank" }
            require(message.isNotBlank()) { "A BLE unavailability message must not be blank" }
        }
    }
}

/**
 * Platform BLE entry point used to check prerequisites before generating transaction material.
 *
 * [capability] does not create listeners, scanners, advertisers, keys, or transaction UUIDs.
 * [create] only constructs a provider; radio resources remain owned by its later `prepare` call.
 */
public interface BleProximityTransportFactory {
    /** Reports runtime availability for exactly the roles a future session would select. */
    public suspend fun capability(roles: BleMdocRoleSelection): BleProximityAvailability

    /** Creates a configured provider without preparing radio resources. */
    public fun create(configuration: BleProximityTransportConfiguration): ProximityTransportProvider
}

internal val BleMdocRoles.selection: BleMdocRoleSelection
    get() = when (this) {
        is BleMdocRoles.CentralClient -> BleMdocRoleSelection.CENTRAL_CLIENT
        is BleMdocRoles.PeripheralServer -> BleMdocRoleSelection.PERIPHERAL_SERVER
        is BleMdocRoles.Dual -> BleMdocRoleSelection.DUAL
    }
