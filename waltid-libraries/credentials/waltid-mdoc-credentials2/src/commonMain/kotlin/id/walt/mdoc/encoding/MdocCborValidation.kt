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

    private fun argument(info: Int): ULong? = when (info) {
        in 0..23 -> info.toULong()
        in 24..27 -> {
            var value = 0uL
            repeat(1 shl (info - 24)) { value = (value shl 8) or byte().toULong() }
            value
        }
        31 -> null
        else -> error("Invalid mdoc CBOR argument")
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
                val keys = mutableSetOf<CborElement>()
                fun entry() {
                    val start = position
                    item(level + 1)
                    if (major == 5) {
                        val key = coseCompliantCbor.decodeFromByteArray(CborElement.serializer(), bytes.copyOfRange(start, position))
                        require(keys.add(key)) { "Duplicate mdoc CBOR map key" }
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
