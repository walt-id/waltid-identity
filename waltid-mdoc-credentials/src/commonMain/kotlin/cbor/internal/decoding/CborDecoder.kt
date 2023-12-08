@file:OptIn(ExperimentalSerializationApi::class)

package cbor.internal.decoding

import cbor.Cbor
import cbor.internal.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

private class CborMapReader(cbor: Cbor, decoder: CborDecoder) : CborListReader(cbor, decoder) {
    override fun skipBeginToken() = setSize(decoder.startMap() * 2)
}

private open class CborListReader(cbor: Cbor, decoder: CborDecoder) : CborReader(cbor, decoder) {
    private var ind = 0

    override fun skipBeginToken() = setSize(decoder.startArray())

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if(descriptor.serialName == "kotlin.collections.ArrayList") {
            decodeByteArrayAsByteString = descriptor.isByteString(0) || descriptor.getElementDescriptor(0).let {
                it.elementsCount > 0 && it.isByteString(0)
            }
        } else if(descriptor.serialName == "kotlin.collections.LinkedHashMap") {
            decodeByteArrayAsByteString = descriptor.isByteString(0) || descriptor.isByteString(1) || descriptor.getElementDescriptor(1).let {
                it.elementsCount > 0 && it.isByteString(0)
            }
        }
        return if (!finiteMode && decoder.isEnd() || (finiteMode && ind >= size)) CompositeDecoder.DECODE_DONE else ind++
    }

}

internal open class CborReader(private val cbor: Cbor, protected val decoder: CborDecoder) : AbstractDecoder() {

    protected var size = -1
        private set
    protected var finiteMode = false
        private set
    private var readProperties: Int = 0

    protected var decodeByteArrayAsByteString = false

    protected fun setSize(size: Int) {
        if (size >= 0) {
            finiteMode = true
            this.size = size
        }
    }

    override val serializersModule: SerializersModule
        get() = cbor.serializersModule

    protected open fun skipBeginToken() = setSize(decoder.startMap())

    @OptIn(ExperimentalSerializationApi::class)
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val re = when (descriptor.kind) {
            StructureKind.LIST, is PolymorphicKind -> CborListReader(cbor, decoder)
            StructureKind.MAP -> CborMapReader(cbor, decoder)
            else -> CborReader(cbor, decoder)
        }
        re.skipBeginToken()
        return re
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (!finiteMode) decoder.end()
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val index = if (cbor.ignoreUnknownKeys) {
            val knownIndex: Int
            while (true) {
                if (isDone()) return CompositeDecoder.DECODE_DONE
                val elemName = decoder.nextString()
                readProperties++

                val index = descriptor.getElementIndex(elemName)
                if (index == CompositeDecoder.UNKNOWN_NAME) {
                    decoder.skipElement()
                } else {
                    knownIndex = index
                    break
                }
            }
            knownIndex
        } else {
            if (isDone()) return CompositeDecoder.DECODE_DONE
            val elemName = decoder.nextString()
            readProperties++
            descriptor.getElementIndexOrThrow(elemName)
        }

        decodeByteArrayAsByteString = descriptor.isByteString(index)
        return index
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        return if (decodeByteArrayAsByteString && deserializer.descriptor == ByteArraySerializer().descriptor) {
            @Suppress("UNCHECKED_CAST")
            decoder.nextByteString() as T
        } else {
            super.decodeSerializableValue(deserializer)
        }
    }

    override fun decodeString() = decoder.nextString()

    override fun decodeNotNullMark(): Boolean = !decoder.isNull()

    override fun decodeDouble() = decoder.nextDouble()
    override fun decodeFloat() = decoder.nextFloat()

    override fun decodeBoolean() = decoder.nextBoolean()

    override fun decodeByte() = decoder.nextNumber().toByte()
    override fun decodeShort() = decoder.nextNumber().toShort()
    override fun decodeChar() = decoder.nextNumber().toInt().toChar()
    override fun decodeInt() = decoder.nextNumber().toInt()
    override fun decodeLong() = decoder.nextNumber()

    override fun decodeNull() = decoder.nextNull()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndexOrThrow(decoder.nextString())

    private fun isDone(): Boolean = !finiteMode && decoder.isEnd() || (finiteMode && readProperties >= size)

    fun peek() = decoder.peek()

    fun decodeByteString() = decoder.nextByteString()

    fun decodeTag() = decoder.readTag()
}

fun Decoder.peek(): Int {
    if(this is CborReader) {
        return this.peek()
    }
    throw SerializationException("Can peek decoder byte only for CborReader")
}

fun Decoder.decodeByteString(): ByteArray {
    if(this is CborReader) {
        return this.decodeByteString()
    }
    throw SerializationException("Decoding of ByteString is only supported for CborReader")
}

fun Decoder.decodeTag(): Long {
    if(this is CborReader) {
        return this.decodeTag()
    }
    throw SerializationException("Decoding of Tag number only supported for CborReader")
}


internal class CborDecoder(private val input: ByteArrayInput) {
    private var curByte: Int = -1

    init {
        readByte()
    }

    private fun readByte(): Int {
        curByte = input.read()
        return curByte
    }

    fun peek(): Int {
        return curByte
    }

    fun isEof() = curByte == -1

    private fun skipByte(expected: Int) {
        if (curByte != expected) throw CborDecodingException("byte ${printByte(expected)}", curByte)
        readByte()
    }

    fun isNull() = curByte == NULL

    fun nextNull(): Nothing? {
        skipOverTags()
        skipByte(NULL)
        return null
    }

    fun nextBoolean(): Boolean {
        skipOverTags()
        val ans = when (curByte) {
            TRUE -> true
            FALSE -> false
            else -> throw CborDecodingException("boolean value", curByte)
        }
        readByte()
        return ans
    }

    fun startArray() = startSized(BEGIN_ARRAY, HEADER_ARRAY, "array")

    fun startMap() = startSized(BEGIN_MAP, HEADER_MAP, "map")

    private fun startSized(unboundedHeader: Int, boundedHeaderMask: Int, collectionType: String): Int {
        skipOverTags()
        if (curByte == unboundedHeader) {
            skipByte(unboundedHeader)
            return -1
        }
        if ((curByte and 0b111_00000) != boundedHeaderMask)
            throw CborDecodingException("start of $collectionType", curByte)
        val size = readNumber().toInt()
        readByte()
        return size
    }

    fun isEnd() = curByte == BREAK

    fun end() = skipByte(BREAK)

    fun nextByteString(): ByteArray {
        skipOverTags()
        if ((curByte and 0b111_00000) != HEADER_BYTE_STRING.toInt())
            throw CborDecodingException("start of byte string", curByte)
        val arr = readBytes()
        readByte()
        return arr
    }

    fun nextString(): String {
        skipOverTags()
        if ((curByte and 0b111_00000) != HEADER_STRING.toInt())
            throw CborDecodingException("start of string", curByte)
        val arr = readBytes()
        val ans = arr.decodeToString()
        readByte()
        return ans
    }

    private fun readBytes(): ByteArray =
        if (curByte and 0b000_11111 == ADDITIONAL_INFORMATION_INDEFINITE_LENGTH) {
            readByte()
            readIndefiniteLengthBytes()
        } else {
            val strLen = readNumber().toInt()
            input.readExactNBytes(strLen)
        }

    private fun skipOverTags() {
        while ((curByte and 0b111_00000) == HEADER_TAG) {
            readNumber() // This is the tag number
            readByte()
        }
    }

    fun readTag(): Long {
        val res = readNumber()
        readByte()
        return res
    }

    fun nextNumber(): Long {
        skipOverTags()
        val res = readNumber()
        readByte()
        return res
    }

    private fun readNumber(): Long {
        val value = curByte and 0b000_11111
        val negative = (curByte and 0b111_00000) == HEADER_NEGATIVE.toInt()
        val bytesToRead = when (value) {
            24 -> 1
            25 -> 2
            26 -> 4
            27 -> 8
            else -> 0
        }
        if (bytesToRead == 0) {
            return if (negative) -(value + 1).toLong()
            else value.toLong()
        }
        val res = input.readExact(bytesToRead)
        return if (negative) -(res + 1)
        else res
    }

    private fun ByteArrayInput.readExact(bytes: Int): Long {
        val arr = readExactNBytes(bytes)
        var result = 0L
        for (i in 0 until bytes) {
            result = (result shl 8) or (arr[i].toInt() and 0xFF).toLong()
        }
        return result
    }

    private fun ByteArrayInput.readExactNBytes(bytesCount: Int): ByteArray {
        if (bytesCount > availableBytes) {
            error("Unexpected EOF, available $availableBytes bytes, requested: $bytesCount")
        }
        val array = ByteArray(bytesCount)
        read(array, 0, bytesCount)
        return array
    }

    fun nextFloat(): Float {
        skipOverTags()
        val res = when (curByte) {
            NEXT_FLOAT -> Float.fromBits(readInt())
            NEXT_HALF -> floatFromHalfBits(readShort())
            else -> throw CborDecodingException("float header", curByte)
        }
        readByte()
        return res
    }

    fun nextDouble(): Double {
        skipOverTags()
        val res = when (curByte) {
            NEXT_DOUBLE -> Double.fromBits(readLong())
            NEXT_FLOAT -> Float.fromBits(readInt()).toDouble()
            NEXT_HALF -> floatFromHalfBits(readShort()).toDouble()
            else -> throw CborDecodingException("double header", curByte)
        }
        readByte()
        return res
    }

    private fun readLong(): Long {
        var result = 0L
        for (i in 0..7) {
            val byte = input.read()
            result = (result shl 8) or byte.toLong()
        }
        return result
    }

    private fun readShort(): Short {
        val highByte = input.read()
        val lowByte = input.read()
        return (highByte shl 8 or lowByte).toShort()
    }

    private fun readInt(): Int {
        var result = 0
        for (i in 0..3) {
            val byte = input.read()
            result = (result shl 8) or byte
        }
        return result
    }

    /**
     * Skips the current value element. Bytes are processed to determine the element type (and corresponding length), to
     * determine how many bytes to skip.
     *
     * For primitive (finite length) elements (e.g. unsigned integer, text string), their length is read and
     * corresponding number of bytes are skipped.
     *
     * For elements that contain children (e.g. array, map), the child count is read and added to a "length stack"
     * (which represents the "number of elements" at each depth of the CBOR data structure). When a child element has
     * been skipped, the "length stack" is [pruned][prune]. For indefinite length elements, a special marker is added to
     * the "length stack" which is only popped from the "length stack" when a CBOR [break][isEnd] is encountered.
     */
    fun skipElement() {
        val lengthStack = mutableListOf<Int>()

        skipOverTags()

        do {
            if (isEof()) throw CborDecodingException("Unexpected EOF while skipping element")

            if (isIndefinite()) {
                lengthStack.add(LENGTH_STACK_INDEFINITE)
            } else if (isEnd()) {
                if (lengthStack.removeLastOrNull() != LENGTH_STACK_INDEFINITE)
                    throw CborDecodingException("next data item", curByte)
                prune(lengthStack)
            } else {
                val header = curByte and 0b111_00000
                val length = elementLength()
                if (header == HEADER_ARRAY || header == HEADER_MAP) {
                    if (length > 0) lengthStack.add(length)
                    skipOverTags()
                } else {
                    input.skip(length)
                    prune(lengthStack)
                }
            }

            readByte()
        } while (lengthStack.isNotEmpty())
    }

    /**
     * Removes an item from the top of the [lengthStack], cascading the removal if the item represents the last item
     * (i.e. a length value of `1`) at its stack depth.
     *
     * For example, pruning a [lengthStack] of `[3, 2, 1, 1]` would result in `[3, 1]`.
     */
    private fun prune(lengthStack: MutableList<Int>) {
        for (i in lengthStack.lastIndex downTo 0) {
            when (lengthStack[i]) {
                LENGTH_STACK_INDEFINITE -> break
                1 -> lengthStack.removeAt(i)
                else -> {
                    lengthStack[i] = lengthStack[i] - 1
                    break
                }
            }
        }
    }

    /**
     * Determines if [curByte] represents an indefinite length CBOR item.
     *
     * Per [RFC 7049: 2.2. Indefinite Lengths for Some Major Types](https://tools.ietf.org/html/rfc7049#section-2.2):
     * > Four CBOR items (arrays, maps, byte strings, and text strings) can be encoded with an indefinite length
     */
    private fun isIndefinite(): Boolean {
        val majorType = curByte and 0b111_00000
        val value = curByte and 0b000_11111

        return value == ADDITIONAL_INFORMATION_INDEFINITE_LENGTH &&
                (majorType == HEADER_ARRAY || majorType == HEADER_MAP ||
                        majorType == HEADER_BYTE_STRING.toInt() || majorType == HEADER_STRING.toInt())
    }

    /**
     * Determines the length of the CBOR item represented by [curByte]; length has specific meaning based on the type:
     *
     * | Major type          | Length represents number of... |
     * |---------------------|--------------------------------|
     * | 0. unsigned integer | bytes                          |
     * | 1. negative integer | bytes                          |
     * | 2. byte string      | bytes                          |
     * | 3. string           | bytes                          |
     * | 4. array            | data items (values)            |
     * | 5. map              | sub-items (keys + values)      |
     * | 6. tag              | bytes                          |
     */
    private fun elementLength(): Int {
        val majorType = curByte and 0b111_00000
        val additionalInformation = curByte and 0b000_11111

        return when (majorType) {
            HEADER_BYTE_STRING.toInt(), HEADER_STRING.toInt(), HEADER_ARRAY -> readNumber().toInt()
            HEADER_MAP -> readNumber().toInt() * 2
            else -> when (additionalInformation) {
                24 -> 1
                25 -> 2
                26 -> 4
                27 -> 8
                else -> 0
            }
        }
    }

    /**
     * Indefinite-length byte sequences contain an unknown number of fixed-length byte sequences (chunks).
     *
     * @return [ByteArray] containing all of the concatenated bytes found in the buffer.
     */
    private fun readIndefiniteLengthBytes(): ByteArray {
        val byteStrings = mutableListOf<ByteArray>()
        do {
            byteStrings.add(readBytes())
            readByte()
        } while (!isEnd())
        return byteStrings.flatten()
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.getElementIndexOrThrow(name: String): Int {
    val index = getElementIndex(name)
    if (index == CompositeDecoder.UNKNOWN_NAME)
        throw SerializationException(
            "$serialName does not contain element with name '$name." +
                    " You can enable 'CborBuilder.ignoreUnknownKeys' property to ignore unknown keys"
        )
    return index
}

private fun Iterable<ByteArray>.flatten(): ByteArray {
    val output = ByteArray(sumOf { it.size })
    var position = 0
    for (chunk in this) {
        chunk.copyInto(output, position)
        position += chunk.size
    }

    return output
}

private val normalizeBaseBits = SINGLE_PRECISION_NORMALIZE_BASE.toBits()


/*
 * For details about half-precision floating-point numbers see https://tools.ietf.org/html/rfc7049#appendix-D
 */
private fun floatFromHalfBits(bits: Short): Float {
    val intBits = bits.toInt()

    val negative = (intBits and 0x8000) != 0
    val halfExp = intBits shr 10 and HALF_PRECISION_MAX_EXPONENT
    val halfMant = intBits and HALF_PRECISION_MAX_MANTISSA

    val exp: Int
    val mant: Int

    when (halfExp) {
        HALF_PRECISION_MAX_EXPONENT -> {
            // if exponent maximal - value is NaN or Infinity
            exp = SINGLE_PRECISION_MAX_EXPONENT
            mant = halfMant
        }

        0 -> {
            if (halfMant == 0) {
                // if exponent and mantissa are zero - value is zero
                mant = 0
                exp = 0
            } else {
                // if exponent is zero and mantissa non-zero - value denormalized. normalize it
                var res = Float.fromBits(normalizeBaseBits + halfMant)
                res -= SINGLE_PRECISION_NORMALIZE_BASE
                return if (negative) -res else res
            }
        }

        else -> {
            // normalized value
            exp = (halfExp + (SINGLE_PRECISION_EXPONENT_BIAS - HALF_PRECISION_EXPONENT_BIAS))
            mant = halfMant
        }
    }

    val res = Float.fromBits((exp shl 23) or (mant shl 13))
    return if (negative) -res else res
}
