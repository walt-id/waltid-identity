package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.crypto.MdocKdf
import id.walt.mdoc.proximity.ImmutableBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Pure ISO/IEC 18013-5 Wi-Fi Aware transaction derivations. */
internal object WifiAwareProtocol {
    private const val DERIVED_BYTES = 32
    private const val SERVICE_BYTES = 16
    private const val MINIMUM_PASSPHRASE_BYTES = 8
    private const val MAXIMUM_PASSPHRASE_BYTES = 63
    private const val SHA_256_BYTES = 32
    private const val HEX = "0123456789ABCDEF"

    fun deriveServiceName(eDeviceKeyBytes: ImmutableBytes): String {
        val derived = derive(eDeviceKeyBytes, "NANService", SERVICE_BYTES)
        return try {
            buildString(SERVICE_BYTES * 2) {
                derived.forEach { byte ->
                    append(HEX[(byte.toInt() ushr 4) and 0x0f])
                    append(HEX[byte.toInt() and 0x0f])
                }
            }
        } finally {
            derived.fill(0)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun derivePassphrase(eDeviceKeyBytes: ImmutableBytes): String {
        val derived = derive(eDeviceKeyBytes, "NANPassphrase", DERIVED_BYTES)
        return try {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(derived)
        } finally {
            derived.fill(0)
        }
    }

    fun requireValidPassphrase(value: String): String = value.also {
        val encoded = it.encodeToByteArray()
        try {
            require(encoded.size in MINIMUM_PASSPHRASE_BYTES..MAXIMUM_PASSPHRASE_BYTES) {
                "A Wi-Fi Aware passphrase must contain 8..63 UTF-8 bytes"
            }
            require(encoded.all { byte -> byte.toInt() in 0x20..0x7e }) {
                "A Wi-Fi Aware passphrase must contain printable ASCII"
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun derive(input: ImmutableBytes, info: String, length: Int): ByteArray {
        val ikm = input.copy()
        return try {
            MdocKdf.deriveSha256(
                inputKeyMaterial = ikm,
                // RFC 5869 represents an absent salt as HashLen zero bytes. The shared
                // HMAC implementation deliberately rejects a zero-length key.
                salt = ByteArray(SHA_256_BYTES),
                info = info.encodeToByteArray(),
                length = length,
            )
        } finally {
            ikm.fill(0)
        }
    }
}

/** Validated NAN Supported Bands bitmap. */
internal class WifiAwareSupportedBands private constructor(private val encoded: ImmutableBytes) {
    fun encoded(): ByteArray = encoded.copy()

    fun intersect(other: WifiAwareSupportedBands): WifiAwareSupportedBands {
        val left = encoded.copy()
        val right = other.encoded.copy()
        val size = minOf(left.size, right.size)
        val result = ByteArray(size) { index -> (left[index].toInt() and right[index].toInt()).toByte() }
        left.fill(0)
        right.fill(0)
        return fromBytes(result)
    }

    override fun equals(other: Any?): Boolean = other is WifiAwareSupportedBands && encoded == other.encoded
    override fun hashCode(): Int = encoded.hashCode()
    override fun toString(): String = "WifiAwareSupportedBands(size=${encoded.size})"

    companion object {
        fun fromBytes(value: ByteArray): WifiAwareSupportedBands {
            require(value.isNotEmpty()) { "Wi-Fi Aware supported bands must not be empty" }
            require(value.any { it != 0.toByte() }) { "Wi-Fi Aware supported bands must select at least one band" }
            return WifiAwareSupportedBands(ImmutableBytes.of(value))
        }
    }
}
