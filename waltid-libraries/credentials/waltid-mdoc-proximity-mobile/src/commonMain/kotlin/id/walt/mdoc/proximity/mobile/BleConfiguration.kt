package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes

/** A transaction-scoped 128-bit BLE service UUID in Device Engagement byte order. */
public class BleServiceUuid private constructor(private val encoded: ImmutableBytes) {
    /** Returns the immutable 16-byte Device Engagement representation. */
    public fun encoded(): ImmutableBytes = encoded

    internal fun platformString(): String {
        val bytes = encoded.copy()
        return buildString(36) {
            bytes.forEachIndexed { index, byte ->
                if (index in HYPHEN_POSITIONS) append('-')
                append(HEX[(byte.toInt() ushr 4) and 0x0f])
                append(HEX[byte.toInt() and 0x0f])
            }
        }
    }

    /** Compares UUID values by their encoded bytes. */
    override fun equals(other: Any?): Boolean = other is BleServiceUuid && encoded == other.encoded

    /** Returns a content-based hash of the encoded UUID. */
    override fun hashCode(): Int = encoded.hashCode()

    /** Returns the canonical lowercase 128-bit hexadecimal representation. */
    override fun toString(): String = platformString()

    /** Creates validated transaction UUID values. */
    public companion object {
        private const val HEX: String = "0123456789abcdef"
        private val HYPHEN_POSITIONS: Set<Int> = setOf(4, 6, 8, 10)
        private val TEXT_HYPHEN_POSITIONS: Set<Int> = setOf(8, 13, 18, 23)

        /** Creates a BLE service UUID from its exact 16-byte representation. */
        public fun fromBytes(encoded: ByteArray): BleServiceUuid {
            require(encoded.size == 16) { "A BLE service UUID must contain exactly 16 bytes" }
            return BleServiceUuid(ImmutableBytes.of(encoded))
        }

        /** Parses a canonical 128-bit hexadecimal UUID string. */
        public fun parse(value: String): BleServiceUuid {
            val valid = value.length == 36 && value.indices.all { index ->
                if (index in TEXT_HYPHEN_POSITIONS) value[index] == '-'
                else value[index] in '0'..'9' || value[index].lowercaseChar() in 'a'..'f'
            }
            require(valid) { "A BLE service UUID must use the canonical 128-bit hexadecimal form" }
            val compact = value.replace("-", "")
            return fromBytes(ByteArray(16) { index -> compact.substring(index * 2, index * 2 + 2).toInt(16).toByte() })
        }
    }
}

/** The mdoc BLE role or role pair advertised in one Device Engagement. */
public sealed interface BleMdocRoles {
    /** The holder scans for the reader service and acts as GATT client. */
    public data class CentralClient(
        /** UUID advertised by the reader for this transaction. */
        public val readerServiceUuid: BleServiceUuid,
    ) : BleMdocRoles

    /** The holder advertises the mdoc service and acts as GATT server. */
    public data class PeripheralServer(
        /** UUID advertised by the holder for this transaction. */
        public val mdocServiceUuid: BleServiceUuid,
    ) : BleMdocRoles

    /** The holder prepares both roles with distinct transaction service UUIDs and keeps the first connection. */
    public data class Dual(
        /** UUID advertised by the reader for the holder's central-client role. */
        public val readerServiceUuid: BleServiceUuid,
        /** UUID advertised by the holder for its peripheral-server role. */
        public val mdocServiceUuid: BleServiceUuid,
    ) : BleMdocRoles {
        init {
            require(readerServiceUuid != mdocServiceUuid) {
                "Dual BLE roles require distinct transaction service UUIDs"
            }
        }
    }
}

/** Bearers the holder may select after a BLE peer is discovered. */
public sealed interface BleBearerPolicy {
    /** Use the mandatory GATT bearer only. */
    public data object GattOnly : BleBearerPolicy

    /** Prefer L2CAP CoC when the peer and platform expose it, otherwise use GATT. */
    public data object PreferL2cap : BleBearerPolicy
}

/**
 * Immutable, session-scoped inputs for a BLE proximity transport provider.
 *
 * @property roles role or role pair selected for Device Engagement
 * @property bearerPolicy permitted GATT/L2CAP selection policy
 * @property eDeviceKeyBytes exact tagged EDeviceKeyBytes encoded into Device Engagement
 */
public data class BleProximityTransportConfiguration(
    public val roles: BleMdocRoles,
    public val bearerPolicy: BleBearerPolicy = BleBearerPolicy.PreferL2cap,
    public val eDeviceKeyBytes: ImmutableBytes,
) {
    init {
        require(eDeviceKeyBytes.size > 0) { "EDeviceKeyBytes must not be empty" }
    }
}
