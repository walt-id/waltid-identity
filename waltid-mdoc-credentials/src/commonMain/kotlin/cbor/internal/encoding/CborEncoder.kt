@file:OptIn(ExperimentalSerializationApi::class)

package cbor.internal.encoding

import cbor.Cbor
import cbor.internal.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlin.experimental.or

// Differs from List only in start byte
private class CborMapWriter(cbor: Cbor, encoder: CborEncoder) : CborListWriter(cbor, encoder) {
    override fun writeBeginToken(numElements: Long) = encoder.startMap(numElements)
}

// Writes all elements consequently, except size - CBOR supports maps and arrays of indefinite length
private open class CborListWriter(cbor: Cbor, encoder: CborEncoder) : CborWriter(cbor, encoder, false) {
    override fun writeBeginToken(numElements: Long) = encoder.startArray(numElements)

    override fun encodeElement(descriptor: SerialDescriptor, index: Int) = true
}

// Writes class as map [fieldName, fieldValue]
internal open class CborWriter(private val cbor: Cbor, protected val encoder: CborEncoder, protected val writeIndefiniteLengthHeader: Boolean) : AbstractEncoder() {
    override val serializersModule: SerializersModule
        get() = cbor.serializersModule

    private var encodeByteArrayAsByteString = false

    @OptIn(ExperimentalSerializationApi::class)
    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        if (encodeByteArrayAsByteString && serializer.descriptor == ByteArraySerializer().descriptor) {
            encoder.encodeByteString(value as ByteArray)
        } else {
            super.encodeSerializableValue(serializer, value)
        }
    }

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = cbor.encodeDefaults

    protected open fun writeBeginToken(numElements: Long) = encoder.startMap(when(writeIndefiniteLengthHeader) {
        true -> null
        else -> numElements
    })

    private fun doBeginStructure(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        val writer = when (descriptor.kind) {
            StructureKind.LIST, is PolymorphicKind -> CborListWriter(cbor, encoder)
            StructureKind.MAP -> CborMapWriter(cbor, encoder)
            else -> CborWriter(cbor, encoder, if(descriptor.elementsCount > 0) {
                IntRange(0, descriptor.elementsCount-1).any { descriptor.isElementOptional(it) }
            } else false)
        }
        writer.writeBeginToken(collectionSize.toLong())
        return writer
    }

    //todo: Write size of map or array if known
    @OptIn(ExperimentalSerializationApi::class)
    override fun beginStructure(descriptor: SerialDescriptor) = doBeginStructure(descriptor, descriptor.elementsCount)

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int) = doBeginStructure(descriptor, collectionSize)

    override fun endStructure(descriptor: SerialDescriptor) = when(writeIndefiniteLengthHeader) {
        true -> encoder.end()
        else -> {}
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        encodeByteArrayAsByteString = descriptor.isByteString(index)
        val name = descriptor.getElementName(index)
        encoder.encodeString(name)
        return true
    }

    override fun encodeString(value: String) = encoder.encodeString(value)

    override fun encodeFloat(value: Float) = encoder.encodeFloat(value)
    override fun encodeDouble(value: Double) = encoder.encodeDouble(value)

    override fun encodeChar(value: Char) = encoder.encodeNumber(value.code.toLong())
    override fun encodeByte(value: Byte) = encoder.encodeNumber(value.toLong())
    override fun encodeShort(value: Short) = encoder.encodeNumber(value.toLong())
    override fun encodeInt(value: Int) = encoder.encodeNumber(value.toLong())
    override fun encodeLong(value: Long) = encoder.encodeNumber(value)

    override fun encodeBoolean(value: Boolean) = encoder.encodeBoolean(value)

    override fun encodeNull() = encoder.encodeNull()

    @OptIn(ExperimentalSerializationApi::class) // KT-46731
    override fun encodeEnum(
        enumDescriptor: SerialDescriptor,
        index: Int
    ) =
        encoder.encodeString(enumDescriptor.getElementName(index))

    fun encodeTag(tag: ULong) = encoder.encodeTag(tag)

    fun encodeByteString(data: ByteArray) = encoder.encodeByteString(data)
}

fun Encoder.encodeTag(tag: ULong) {
    if(this is CborWriter) {
        this.encodeTag(tag)
    }
}

fun Encoder.encodeByteString(data: ByteArray) {
    if(this is CborWriter) {
        this.encodeByteString(data)
    }
}


// For details of representation, see https://tools.ietf.org/html/rfc7049#section-2.1
internal class CborEncoder(private val output: ByteArrayOutput) {

    fun startArray(numElements: Long) =
        encodeDataHeader(HEADER_ARRAY.toByte(), numElements)

    fun startMap(numElements: Long?) =
        if(numElements != null)
            encodeDataHeader(HEADER_MAP.toByte(), numElements)
        else
            output.write(BEGIN_MAP)

    fun end() {
        output.write(BREAK)
    }

    fun encodeNull() = output.write(NULL)

    fun encodeBoolean(value: Boolean) = output.write(if (value) TRUE else FALSE)

    fun encodeNumber(value: Long) = output.write(composeNumber(value))

    fun encodeByteString(data: ByteArray) {
        encodeByteArray(data, HEADER_BYTE_STRING)
    }

    fun encodeString(value: String) {
        encodeByteArray(value.encodeToByteArray(), HEADER_STRING)
    }

    fun encodeTag(tag: ULong) = encodeDataHeader(HEADER_TAG.toByte(), tag.toLong())

    private fun encodeDataHeader(type: Byte, value: Long) {
        val header = composeNumber(value)
        header[0] = header[0] or type
        output.write(header)
    }

    private fun encodeByteArray(data: ByteArray, type: Byte) {
        val header = composeNumber(data.size.toLong())
        header[0] = header[0] or type
        output.write(header)
        output.write(data)
    }

    fun encodeFloat(value: Float) {
        output.write(NEXT_FLOAT)
        val bits = value.toRawBits()
        for (i in 0..3) {
            output.write((bits shr (24 - 8 * i)) and 0xFF)
        }
    }

    fun encodeDouble(value: Double) {
        output.write(NEXT_DOUBLE)
        val bits = value.toRawBits()
        for (i in 0..7) {
            output.write(((bits shr (56 - 8 * i)) and 0xFF).toInt())
        }
    }

    private fun composeNumber(value: Long): ByteArray =
        if (value >= 0) composePositive(value.toULong()) else composeNegative(value)

    private fun composePositive(value: ULong): ByteArray = when (value) {
        in 0u..23u -> byteArrayOf(value.toByte())
        in 24u..UByte.MAX_VALUE.toUInt() -> byteArrayOf(24, value.toByte())
        in (UByte.MAX_VALUE.toUInt() + 1u)..UShort.MAX_VALUE.toUInt() -> encodeToByteArray(value, 2, 25)
        in (UShort.MAX_VALUE.toUInt() + 1u)..UInt.MAX_VALUE -> encodeToByteArray(value, 4, 26)
        else -> encodeToByteArray(value, 8, 27)
    }

    private fun encodeToByteArray(value: ULong, bytes: Int, tag: Byte): ByteArray {
        val result = ByteArray(bytes + 1)
        val limit = bytes * 8 - 8
        result[0] = tag
        for (i in 0 until bytes) {
            result[i + 1] = ((value shr (limit - 8 * i)) and 0xFFu).toByte()
        }
        return result
    }

    private fun composeNegative(value: Long): ByteArray {
        val aVal = if (value == Long.MIN_VALUE) Long.MAX_VALUE else -1 - value
        val data = composePositive(aVal.toULong())
        data[0] = data[0] or HEADER_NEGATIVE
        return data
    }
}
