@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE", "UNCHECKED_CAST")
@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.dataelement

import cbor.Cbor
import cbor.internal.*
import cbor.internal.decoding.decodeByteString
import cbor.internal.decoding.decodeTag
import cbor.internal.decoding.peek
import cbor.internal.encoding.encodeByteString
import cbor.internal.encoding.encodeTag
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Data Element Type,
 * see also:
 * CBOR Major types: https://www.rfc-editor.org/rfc/rfc8949#name-major-types
 * CBOR Prelude: https://www.rfc-editor.org/rfc/rfc8610.html#appendix-D
 * CBOR date extension: https://datatracker.ietf.org/doc/html/rfc8943
 */
enum class DEType {
    number,     // #0, #1, #7.25, #7.26, #7.27
    boolean,    // #7.20, #7.21
    textString, // #3
    byteString, // #2
    nil,        // #7.22
    dateTime,   // #6.0, #6.1
    fullDate,   // #6.1004, #6.100
    list,       // #4
    map,        // #5,
    encodedCbor // #6.24
}

/**
 * Data Element attribute
 */
open class DEAttribute(val type: DEType)

/**
 * Data Element DateTime mode: tdate (rfc3339 string) or time since epoch (as int/long or floating point)
 */
enum class DEDateTimeMode {
    tdate,          // #6.0
    time_int,       // #6.1
    time_float,     // #6.1
    time_double,    // #6.1
}

/**
 * Data Element full date mode: rfc3339 string date-only part or number of days since epoch as int/long
 */
enum class DEFullDateMode {
    full_date_str,  // #6.1004
    full_date_int   // #6.100
}

/**
 * Data Element DateTime attribute
 */
class DEDateTimeAttribute(val mode: DEDateTimeMode = DEDateTimeMode.tdate) : DEAttribute(DEType.dateTime)

/**
 * Data Element full date attribute
 */
class DEFullDateAttribute(val mode: DEFullDateMode = DEFullDateMode.full_date_str) : DEAttribute(DEType.fullDate)

@Serializer(forClass = DataElement::class)
@OptIn(ExperimentalSerializationApi::class)
internal object DataElementSerializer : KSerializer<DataElement> {

    override fun deserialize(decoder: Decoder): DataElement {
        val curHead = decoder.peek()
        return when (val majorType = curHead.shr(5)) {
            0, 1 -> NumberElement(decoder.decodeLong())
            2 -> ByteStringElement(decoder.decodeByteString())
            3 -> StringElement(decoder.decodeString())
            4 -> ListElement(decoder.decodeSerializableValue(ListSerializer(DataElementSerializer)))
            5 -> MapElement(decoder.decodeSerializableValue(MapSerializer(MapKeySerializer, DataElementSerializer)))
            6 -> {
                when (val tag = decoder.decodeTag()) {
                    ENCODED_CBOR -> EncodedCBORElement(decoder.decodeByteString())
                    TDATE, TIME -> deserializeDateTime(decoder, tag)
                    FULL_DATE_STR, FULL_DATE_INT -> deserializeFullDate(decoder, tag)
                    COSE_SIGN1 -> ListElement(decoder.decodeSerializableValue(ListSerializer(DataElementSerializer)))
                    else -> throw SerializationException("The given tagged value type $tag is currently not supported")
                }
            }

            7 -> when (curHead) {
                FALSE, TRUE -> BooleanElement(decoder.decodeBoolean())
                NULL -> NullElement(decoder.decodeNull())
                NEXT_HALF, NEXT_FLOAT, NEXT_DOUBLE -> NumberElement(decoder.decodeDouble())
                else -> throw SerializationException("DataElement value mustn't be contentless data")
            }

            else -> throw SerializationException("Cannot deserialize value with given major type $majorType")
        }
    }

    override fun serialize(encoder: Encoder, value: DataElement) {
        when (value.type) {
            DEType.number -> when (value.internalValue) {
                is Int, is Long, is Short -> encoder.encodeLong((value.internalValue as Number).toLong())
                is Float -> encoder.encodeFloat(value.internalValue.toFloat())
                is Double -> encoder.encodeDouble(value.internalValue.toDouble())
            }

            DEType.boolean -> encoder.encodeBoolean(value.internalValue as Boolean)
            DEType.textString -> encoder.encodeString(value.internalValue as String)
            DEType.byteString -> encoder.encodeByteString(value.internalValue as ByteArray)
            DEType.list -> encoder.encodeSerializableValue(
                ListSerializer(DataElementSerializer),
                value.internalValue as List<DataElement>
            )

            DEType.map -> encoder.encodeSerializableValue(
                MapSerializer(MapKeySerializer, DataElementSerializer),
                value.internalValue as Map<MapKey, DataElement>
            )

            DEType.nil -> encoder.encodeNull()
            DEType.encodedCbor -> {
                encoder.encodeTag(ENCODED_CBOR.toULong())
                encoder.encodeByteString(value.internalValue as ByteArray)
            }

            DEType.dateTime -> serializeDateTime(encoder, value as DateTimeElement)
            DEType.fullDate -> serializeFullDate(encoder, value as FullDateElement)
        }
    }

    private fun serializeDateTime(encoder: Encoder, dateTime: DateTimeElement) {
        val attribute = dateTime.attribute as DEDateTimeAttribute
        when (attribute.mode) {
            DEDateTimeMode.tdate -> {
                encoder.encodeTag(TDATE.toULong())
                encoder.encodeString(dateTime.value.toString())
            }

            DEDateTimeMode.time_int, DEDateTimeMode.time_float, DEDateTimeMode.time_double -> {
                encoder.encodeTag(TIME.toULong())
                when (attribute.mode) {
                    DEDateTimeMode.time_int -> encoder.encodeLong(dateTime.value.epochSeconds)
                    DEDateTimeMode.time_float -> encoder.encodeFloat(dateTime.value.toEpochMilliseconds().toFloat() / 1000f)
                    DEDateTimeMode.time_double -> encoder.encodeDouble(dateTime.value.toEpochMilliseconds().toDouble() / 1000.0)
                    else -> {} // not possible
                }
            }
        }
    }

    private fun deserializeDateTime(decoder: Decoder, tag: Long): DateTimeElement {
        val nextHead = decoder.peek()
        return when (tag) {
            TDATE -> TDateElement(Instant.parse(decoder.decodeString()))
            TIME -> when (nextHead) {
                NEXT_HALF, NEXT_FLOAT -> DateTimeElement(
                    Instant.fromEpochMilliseconds((decoder.decodeFloat() * 1000.0f).toLong()),
                    DEDateTimeMode.time_float
                )

                NEXT_DOUBLE -> DateTimeElement(
                    Instant.fromEpochMilliseconds((decoder.decodeDouble() * 1000.0).toLong()),
                    DEDateTimeMode.time_double
                )

                else -> DateTimeElement(Instant.fromEpochSeconds(decoder.decodeLong()), DEDateTimeMode.time_int)
            }

            else -> throw SerializationException("Unsupported tag number for DateTime value: #6.$tag, supported tags are #6.0, #6.1")
        }

    }

    private fun serializeFullDate(encoder: Encoder, date: FullDateElement) {
        val attribute = date.attribute as DEFullDateAttribute
        when (attribute.mode) {
            DEFullDateMode.full_date_str -> {
                encoder.encodeTag(FULL_DATE_STR.toULong())
                encoder.encodeString(date.value.toString())
            }

            DEFullDateMode.full_date_int -> {
                encoder.encodeTag(FULL_DATE_INT.toULong())
                encoder.encodeInt(date.value.toEpochDays())
            }
        }
    }

    private fun deserializeFullDate(decoder: Decoder, tag: Long): FullDateElement {
        return when (tag) {
            FULL_DATE_STR -> FullDateElement(LocalDate.parse(decoder.decodeString()), DEFullDateMode.full_date_str)
            FULL_DATE_INT -> FullDateElement(LocalDate.fromEpochDays(decoder.decodeLong().toInt()), DEFullDateMode.full_date_int)
            else -> throw SerializationException("Unsupported tag number for full-date: #6.$tag, supported tags are #6.1004, #6.100")
        }
    }

}

/**
 * Generic CBOR data element
 */
@Serializable(with = DataElementSerializer::class)
abstract class DataElement(
    val internalValue: Any?, val attribute: DEAttribute,
) {
    val type
        get() = attribute.type

    override fun equals(other: Any?): Boolean {
        val res = other is DataElement && other.type == type && when (type) {
            DEType.byteString, DEType.encodedCbor -> (internalValue as ByteArray).contentEquals(other.internalValue as ByteArray)
            DEType.list -> (internalValue as List<DataElement>).all { (other.internalValue as List<DataElement>).contains(it) }
            DEType.map -> (internalValue as Map<MapKey, DataElement>).all { (other.internalValue as Map<MapKey, DataElement>)[it.key] == it.value }
            else -> internalValue == other.internalValue
        }
        return res
    }

    override fun hashCode(): Int {
        return internalValue.hashCode()
    }

    /**
     * Serialize to CBOR data as byte array
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun toCBOR() = Cbor.encodeToByteArray(DataElementSerializer, this)

    /**
     * Serialize to CBOR hex string
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun toCBORHex() = Cbor.encodeToHexString(DataElementSerializer, this)

    /**
     * Serialize and wrap in EncodedCBORElement (#6.24-tagged CBOR data element)
     */
    fun toEncodedCBORElement() = EncodedCBORElement(this.toCBOR())

    companion object {
        /**
         * Deserialize data element from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun <T : DataElement> fromCBOR(cbor: ByteArray): T {
            return Cbor.decodeFromByteArray(DataElementSerializer, cbor) as T
        }

        /**
         * Deserialize data element from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun <T : DataElement> fromCBORHex(cbor: String): T {
            return Cbor.decodeFromHexString(DataElementSerializer, cbor) as T
        }
    }
}

typealias AnyDataElement = DataElement

/**
 * Number element for Long, Int, UInt, float, double, ...
 */
@Serializable(with = DataElementSerializer::class)
class NumberElement(val value: Number) : DataElement(value, DEAttribute(DEType.number)) {
    constructor(value: UInt) : this(value.toLong())
}

/**
 * Boolean element
 */
@Serializable(with = DataElementSerializer::class)
class BooleanElement(val value: Boolean) : DataElement(value, DEAttribute(DEType.boolean))

/**
 * String element
 */
@Serializable(with = DataElementSerializer::class)
class StringElement(val value: String) : DataElement(value, DEAttribute(DEType.textString))

/**
 * Byte string element
 */
@Serializable(with = DataElementSerializer::class)
class ByteStringElement(val value: ByteArray) : DataElement(value, DEAttribute(DEType.byteString))

/**
 * List element
 */
@Serializable(with = DataElementSerializer::class)
class ListElement(val value: List<DataElement>) : DataElement(value, DEAttribute(DEType.list)) {
    constructor() : this(listOf())
}

/**
 * Map (object) element
 * Supports int or string keys
 */
@Serializable(with = DataElementSerializer::class)
class MapElement(val value: Map<MapKey, DataElement>) : DataElement(value, DEAttribute(DEType.map))

/**
 * Null element
 */
@Serializable(with = DataElementSerializer::class)
class NullElement(val value: Nothing? = null) : DataElement(value, DEAttribute(DEType.nil))

/**
 * Date element for CBOR tagged dates:
 * tdate (RFC3339 string): #6.0, time (seconds since epoch): #6.1
 */
@Serializable(with = DataElementSerializer::class)
open class DateTimeElement(val value: Instant, subType: DEDateTimeMode = DEDateTimeMode.tdate) :
    DataElement(value, DEDateTimeAttribute(subType))

/**
 * TDate element (RFC3339 string, #6.0)
 */
@Serializable(with = DataElementSerializer::class)
class TDateElement(value: Instant) : DateTimeElement(value, DEDateTimeMode.tdate)

/*
 * Full date element: #6.1004 (RFC 3339 full-date string), #6.100 (Number of days since epoch)
 */
@Serializable(with = DataElementSerializer::class)
class FullDateElement(val value: LocalDate, subType: DEFullDateMode = DEFullDateMode.full_date_str) :
    DataElement(value, DEFullDateAttribute(subType))

/**
 * Encoded CBOR element (tagged CBOR data, #6.24)
 */
@Serializable(with = DataElementSerializer::class)
class EncodedCBORElement(val value: ByteArray) : DataElement(value, DEAttribute(DEType.encodedCbor)) {
    constructor(element: DataElement) : this(element.toCBOR())

    /**
     * Decode encoded data element
     */
    fun decode(): DataElement {
        return fromCBOR(value)
    }

    /**
     * Decode encoded data element to specific data element type
     */
    inline fun <reified T : DataElement> decodeDataElement(): T {
        return fromCBOR(value)
    }

    /**
     * Decode encoded data element to specific type
     */
    inline fun <reified T> decode(): T {
        return Cbor.decodeFromByteArray(value)
    }

    companion object {
        /**
         * Create from CBOR data to be wrapped by this encoded CBOR element
         */
        fun fromEncodedCBORElementData(data: ByteArray): EncodedCBORElement = fromCBOR(data)
    }
}

