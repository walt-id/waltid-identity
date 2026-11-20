@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.encoding

import id.walt.cose.coseCompliantCbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement

/**
 * Reject ambiguous maps before decoding into models, whose serializers can overwrite duplicate keys.
 * Also checks CBOR embedded with tag 24 without changing any signed bytes. Map ordering and
 * indefinite-length containers are preserved; re-encoding is not an integrity check.
 */
fun validateMdocCbor(bytes: ByteArray) {
    MdocCborValidator(bytes).validate()
}

private class MdocCborValidator(private val bytes: ByteArray, private val depth: Int = 0) {
    private var position = 0

    fun validate() {
        item(depth)
        require(position == bytes.size) { "Trailing data after mdoc CBOR" }
    }

    private fun byte(): Int {
        require(position < bytes.size) { "Truncated mdoc CBOR" }
        return bytes[position++].toInt() and 0xff
    }

    private fun argument(info: Int): ULong? {
        require(info in 0..27 || info == 31) { "Invalid mdoc CBOR argument" }
        return when {
            info < 24 -> info.toULong()
            info == 31 -> null
            else -> {
                var value = 0uL
                repeat(1 shl (info - 24)) { value = (value shl 8) or byte().toULong() }
                value
            }
        }
    }

    private sealed interface MapKey
    private data class IntegerKey(val negative: Boolean, val argument: ULong) : MapKey
    private data class ElementKey(val value: CborElement) : MapKey

    /** Typed map decoders ignore outer key tags; integer widths must not distinguish the same label. */
    private fun mapKey(start: Int, end: Int): MapKey {
        position = start
        var initial = byte()
        while (initial shr 5 == 6) {
            argument(initial and 31)
            initial = byte()
        }
        val major = initial shr 5
        val key = if (major == 0 || major == 1) {
            // Keep the CBOR argument, not its signed magnitude: -(2^64) is a valid key.
            IntegerKey(major == 1, requireNotNull(argument(initial and 31)))
        } else {
            ElementKey(coseCompliantCbor.decodeFromByteArray(CborElement.serializer(), bytes.copyOfRange(position - 1, end)))
        }
        position = end
        return key
    }

    private fun item(level: Int) {
        require(level < 64) { "Mdoc CBOR nesting exceeds limit" }
        val initial = byte()
        val major = initial shr 5
        val value = argument(initial and 31)
        when (major) {
            0, 1, 7 -> require(value != null) { "Unexpected CBOR break or indefinite scalar" }
            2, 3 -> if (value != null) {
                require(value <= (bytes.size - position).toULong()) { "Truncated mdoc CBOR string" }
                position += value.toInt()
            } else {
                while (!atBreak()) {
                    require(position < bytes.size && (bytes[position].toInt() and 0xff) shr 5 == major &&
                        (bytes[position].toInt() and 31) != 31) { "Invalid indefinite CBOR string chunk" }
                    item(level + 1)
                }
                position++
            }
            4, 5 -> {
                val keys = mutableSetOf<MapKey>()
                fun entry() {
                    val start = position
                    item(level + 1)
                    if (major == 5) {
                        require(keys.add(mapKey(start, position))) { "Duplicate mdoc CBOR map key" }
                        item(level + 1)
                    }
                }
                if (value != null) {
                    require(value <= (bytes.size - position).toULong()) { "Truncated mdoc CBOR container" }
                    repeat(value.toInt()) { entry() }
                } else {
                    while (!atBreak()) entry()
                    position++
                }
            }
            6 -> {
                require(value != null) { "Indefinite CBOR tag" }
                val start = position
                item(level + 1)
                if (value == 24uL) {
                    val embedded = coseCompliantCbor.decodeFromByteArray(CborElement.serializer(), bytes.copyOfRange(start, position))
                    require(embedded is CborByteString) { "CBOR tag 24 must contain a byte string" }
                    MdocCborValidator(embedded.toByteArray(), level + 1).validate()
                }
            }
        }
    }

    private fun atBreak(): Boolean = position < bytes.size && bytes[position] == 0xff.toByte()
}
