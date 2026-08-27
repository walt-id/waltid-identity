package id.walt.mdoc.proximity

/**
 * A small structural guard run before kotlinx.serialization.
 *
 * It bounds nesting and item count, rejects truncated/trailing data and duplicate map keys, and does not
 * interpret protocol fields. Typed serializers remain responsible for field-level validation.
 */
internal object MdocCborGuard {
    fun validate(bytes: ByteArray, maximumDepth: Int, maximumItems: Int) {
        require(maximumDepth > 0 && maximumItems > 0)
        Parser(bytes, maximumDepth, maximumItems).validate()
    }

    private class Parser(
        private val bytes: ByteArray,
        private val maximumDepth: Int,
        private val maximumItems: Int,
    ) {
        private var offset = 0
        private var items = 0

        fun validate() {
            if (bytes.isEmpty()) invalid("CBOR input is empty")
            item(0)
            if (offset != bytes.size) invalid("CBOR input contains trailing data")
        }

        private fun item(depth: Int): KeyIdentity {
            if (depth > maximumDepth) invalid("CBOR nesting exceeds the configured limit")
            if (++items > maximumItems) invalid("CBOR item count exceeds the configured limit")
            val start = offset
            val initial = readByte()
            val major = initial ushr 5
            val additional = initial and 0x1f
            return when (major) {
                0 -> KeyIdentity.Integer(false, argument(additional))
                1 -> KeyIdentity.Integer(true, argument(additional))
                2, 3 -> byteOrText(major, additional)
                4 -> {
                    sequence(additional) { item(depth + 1) }
                    KeyIdentity.Raw(bytes.copyOfRange(start, offset).asContentKey())
                }
                5 -> {
                    map(additional, depth)
                    KeyIdentity.Raw(bytes.copyOfRange(start, offset).asContentKey())
                }
                6 -> {
                    argument(additional)
                    item(depth + 1)
                    KeyIdentity.Raw(bytes.copyOfRange(start, offset).asContentKey())
                }
                7 -> {
                    simple(additional)
                    KeyIdentity.Raw(bytes.copyOfRange(start, offset).asContentKey())
                }
                else -> invalid("Unsupported CBOR major type")
            }
        }

        private fun byteOrText(major: Int, additional: Int): KeyIdentity {
            val content = readBytes(argument(additional))
            return KeyIdentity.Bytes(major, content.asContentKey())
        }

        private inline fun sequence(additional: Int, consume: () -> Unit) {
            repeatCount(argument(additional)) { consume() }
        }

        private fun map(additional: Int, depth: Int) {
            val seen = mutableSetOf<KeyIdentity>()
            val consumeEntry = {
                val key = item(depth + 1)
                if (!seen.add(key)) invalid("CBOR map contains a duplicate key")
                item(depth + 1)
                Unit
            }
            repeatCount(argument(additional)) { consumeEntry() }
        }

        private fun simple(additional: Int) {
            when (additional) {
                in 0..23 -> Unit
                24 -> if (unsigned(1) < 32uL) invalid("CBOR simple value is not in preferred form")
                25 -> validateHalf(unsigned(2).toInt())
                26 -> validateFloat(unsigned(4).toUInt().toInt())
                27 -> validateDouble(unsigned(8).toLong())
                31 -> invalid("Unexpected CBOR break marker")
                else -> invalid("Reserved CBOR additional information")
            }
        }

        private fun argument(additional: Int): ULong = when (additional) {
            in 0..23 -> additional.toULong()
            24 -> unsigned(1).also { if (it < 24uL) invalid("CBOR argument is not in preferred form") }
            25 -> unsigned(2).also { if (it <= UByte.MAX_VALUE.toULong()) invalid("CBOR argument is not in preferred form") }
            26 -> unsigned(4).also { if (it <= UShort.MAX_VALUE.toULong()) invalid("CBOR argument is not in preferred form") }
            27 -> unsigned(8).also { if (it <= UInt.MAX_VALUE.toULong()) invalid("CBOR argument is not in preferred form") }
            31 -> invalid("Indefinite-length CBOR is not permitted")
            else -> invalid("Reserved CBOR additional information")
        }

        private fun unsigned(count: Int): ULong {
            var value = 0uL
            repeat(count) { value = (value shl 8) or readByte().toULong() }
            return value
        }

        private fun readByte(): Int {
            if (offset >= bytes.size) invalid("CBOR input is truncated")
            return bytes[offset++].toInt() and 0xff
        }

        private fun readBytes(length: ULong): ByteArray {
            if (length > Int.MAX_VALUE.toULong()) invalid("CBOR value is too large")
            val count = length.toInt()
            if (count > bytes.size - offset) invalid("CBOR input is truncated")
            return bytes.copyOfRange(offset, offset + count).also { offset += count }
        }

        private inline fun repeatCount(count: ULong, block: () -> Unit) {
            if (count > Int.MAX_VALUE.toULong()) invalid("CBOR collection is too large")
            repeat(count.toInt()) { block() }
        }

        private fun validateHalf(bits: Int) {
            val exponent = bits and 0x7c00
            val fraction = bits and 0x03ff
            if (exponent == 0x7c00 && fraction != 0 && bits != 0x7e00) {
                invalid("CBOR NaN is not in preferred form")
            }
        }

        private fun validateFloat(bits: Int) {
            val value = Float.fromBits(bits)
            if (value.isNaN() || halfToFloat(floatToHalf(value)).toRawBits() == value.toRawBits()) {
                invalid("CBOR floating-point value is not in preferred form")
            }
        }

        private fun validateDouble(bits: Long) {
            val value = Double.fromBits(bits)
            if (value.isNaN() || value.toFloat().toDouble().toRawBits() == value.toRawBits()) {
                invalid("CBOR floating-point value is not in preferred form")
            }
        }

        private fun floatToHalf(value: Float): Int {
            val bits = value.toRawBits()
            val sign = (bits ushr 16) and 0x8000
            val magnitude = bits and 0x7fffffff
            val rounded = magnitude + 0x1000
            return when {
                rounded >= 0x47800000 -> when {
                    magnitude < 0x47800000 -> sign or 0x7bff
                    magnitude < 0x7f800000 -> sign or 0x7c00
                    else -> sign or 0x7c00 or ((magnitude and 0x7fffff) ushr 13)
                }
                rounded >= 0x38800000 -> sign or ((rounded - 0x38000000) ushr 13)
                rounded < 0x33000000 -> sign
                else -> {
                    val exponent = magnitude ushr 23
                    sign or ((((magnitude and 0x7fffff) or 0x800000) +
                        (0x800000 ushr (exponent - 102))) ushr (126 - exponent))
                }
            }
        }

        private fun halfToFloat(half: Int): Float {
            val sign = (half and 0x8000) shl 16
            var exponent = (half ushr 10) and 0x1f
            var fraction = half and 0x03ff
            val bits = when (exponent) {
                0 -> {
                    if (fraction == 0) sign
                    else {
                        while (fraction and 0x0400 == 0) {
                            fraction = fraction shl 1
                            exponent--
                        }
                        fraction = fraction and 0x03ff
                        sign or ((exponent + 113) shl 23) or (fraction shl 13)
                    }
                }
                31 -> sign or 0x7f800000 or (fraction shl 13)
                else -> sign or ((exponent + 112) shl 23) or (fraction shl 13)
            }
            return Float.fromBits(bits)
        }

        private fun invalid(message: String): Nothing = throw MdocCborValidationException(message)
    }

    private sealed interface KeyIdentity {
        data class Integer(val negative: Boolean, val value: ULong) : KeyIdentity
        data class Bytes(val major: Int, val value: ContentKey) : KeyIdentity
        data class Raw(val value: ContentKey) : KeyIdentity
    }

    private class ContentKey(private val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean = other is ContentKey && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    private fun ByteArray.asContentKey() = ContentKey(this)
}

internal class MdocCborValidationException(message: String) : Exception(message)
