package id.walt.crypto.utils

import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.MultiBaseUtils.decodeMultiBase58Btc
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

// https://github.com/multiformats/multicodec
// https://github.com/multiformats/multicodec/blob/master/table.csv
// 0x1205 rsa-pub
// 0xed ed25519-pub
// 0xe7 secp256k1-pub
// 0xeb51 jwk_jcs-pub

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
object MultiCodecUtils {
    const val JwkJcsPubMultiCodecKeyCode = 0xeb51u

    fun getMultiCodecKeyCode(keyType: KeyType) = when (keyType) {
        KeyType.Ed25519 -> 0xEDu
        KeyType.secp256k1 -> 0xE7u
        KeyType.secp256r1 -> 0x1200u
        KeyType.RSA -> 0x1205u
    }

    fun getKeyTypeFromKeyCode(code: UInt): KeyType = when (code) {
        0xEDu -> KeyType.Ed25519
        0xE7u -> KeyType.secp256k1
        0x1205u -> KeyType.RSA
        0x1200u -> KeyType.secp256r1
        else -> throw IllegalArgumentException("No multicodec algorithm for code: $code")
    }

    @JsName("getMultiCodecKeyCodeUsingString")
    fun getMultiCodecKeyCode(mb: String): UInt = UVarInt.fromBytes(decodeMultiBase58Btc(mb)).value

    /**
     * Unsigned variable-length integer
     * https://github.com/multiformats/unsigned-varint
     * Used for multicodec: https://github.com/multiformats/multicodec
     */
    class UVarInt(val value: UInt) {
        val bytes: ByteArray = bytesFromUInt(value)
        val length
            get() = bytes.size

        private fun bytesFromUInt(num: UInt): ByteArray {
            val varInt = mutableListOf<Byte>()
            var rest = num
            while ((rest and MSBALL) != 0u) {
                varInt.add(((rest and 0xFFu) or MSB).toByte())
                rest = rest.shr(7)
            }
            varInt.add(rest.toByte())
            return varInt.toByteArray()
        }

        override fun toString(): String {
            return "0x${value.toString(16)}"
        }

        companion object {
            val MSB = 0x80u
            val LSB = 0x7Fu
            val MSBALL = 0xFFFFFF80u

            fun fromBytes(bytes: ByteArray): UVarInt {
                if (bytes.isEmpty())
                    throw IllegalArgumentException("Empty byte array")

                var idx = 0
                var value = (bytes[idx].toUInt() and LSB)
                while (idx + 1 < bytes.size && (bytes[idx].toUInt() and MSB) != 0u) {
                    idx++
                    value = value or (bytes[idx].toUInt() and LSB).shl(idx * 7)
                }
                return UVarInt(value)
            }
        }
    }
}
