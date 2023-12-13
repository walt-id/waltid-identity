package id.walt.mdoc.dataelement

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

@Serializer(forClass = DataElement::class)
@OptIn(ExperimentalSerializationApi::class)
internal object DataElementSerializer: KSerializer<AnyDataElement> {

  override fun deserialize(decoder: Decoder): AnyDataElement {
    val curHead = decoder.peek()
    val majorType = curHead.shr(5)
    return when(majorType) {
      0, 1 -> NumberElement(decoder.decodeLong())
      2 -> ByteStringElement(decoder.decodeByteString())
      3 -> StringElement(decoder.decodeString())
      4 -> ListElement(decoder.decodeSerializableValue(ListSerializer(DataElementSerializer)))
      5 -> MapElement(decoder.decodeSerializableValue(MapSerializer(MapKeySerializer, DataElementSerializer)))
      6 -> {
        val tag = decoder.decodeTag()
        when(tag) {
          ENCODED_CBOR -> EncodedCBORElement(decoder.decodeByteString())
          TDATE, TIME -> deserializeDateTime(decoder, tag)
          FULL_DATE_STR, FULL_DATE_INT -> deserializeFullDate(decoder, tag)
          COSE_SIGN1 -> ListElement(decoder.decodeSerializableValue(ListSerializer(DataElementSerializer)))
          else -> throw SerializationException("The given tagged value type $tag is currently not supported")
        }
      }
      7 -> when(curHead) {
        FALSE, TRUE -> BooleanElement(decoder.decodeBoolean())
        NULL -> NullElement(decoder.decodeNull())
        NEXT_HALF, NEXT_FLOAT, NEXT_DOUBLE -> NumberElement(decoder.decodeDouble())
        else -> throw SerializationException("DataElement value mustn't be contentless data")
      }
      else -> throw SerializationException("Cannot deserialize value with given major type $majorType")
    }
  }

  override fun serialize(encoder: Encoder, element: AnyDataElement) {
    when(element.type) {
      DEType.number -> when(element.value) {
        is Int, is Long, is Short -> encoder.encodeLong((element.value as Number).toLong())
        is Float -> encoder.encodeFloat(element.value.toFloat())
        is Double -> encoder.encodeDouble(element.value.toDouble())
      }
      DEType.boolean -> encoder.encodeBoolean(element.value as Boolean)
      DEType.textString -> encoder.encodeString(element.value as String)
      DEType.byteString -> encoder.encodeByteString(element.value as ByteArray)
      DEType.list -> encoder.encodeSerializableValue(
        ListSerializer(DataElementSerializer),
        element.value as List<AnyDataElement>
      )
      DEType.map -> encoder.encodeSerializableValue(
        MapSerializer(MapKeySerializer, DataElementSerializer),
        element.value as Map<MapKey, AnyDataElement>
      )
      DEType.nil -> encoder.encodeNull()
      DEType.encodedCbor -> {
        encoder.encodeTag(ENCODED_CBOR.toULong())
        encoder.encodeByteString(element.value as ByteArray)
      }
      DEType.dateTime -> serializeDateTime(encoder, element as DateTimeElement)
      DEType.fullDate -> serializeFullDate(encoder, element as FullDateElement)
    }
  }

  private fun serializeDateTime(encoder: Encoder, element: DateTimeElement) {
    val attribute = element.attribute as DEDateTimeAttribute
    when(attribute.mode) {
      DEDateTimeMode.tdate -> {
          encoder.encodeTag(TDATE.toULong())
          encoder.encodeString(element.value.toString())
        }
      DEDateTimeMode.time_int, DEDateTimeMode.time_float, DEDateTimeMode.time_double -> {
        encoder.encodeTag(TIME.toULong())
        when(attribute.mode) {
          DEDateTimeMode.time_int -> encoder.encodeLong(element.value.epochSeconds)
          DEDateTimeMode.time_float -> encoder.encodeFloat(element.value.toEpochMilliseconds().toFloat() / 1000f)
          DEDateTimeMode.time_double -> encoder.encodeDouble(element.value.toEpochMilliseconds().toDouble() / 1000.0)
          else -> {} // not possible
        }
      }
    }
  }

  private fun deserializeDateTime(decoder: Decoder, tag: Long): DateTimeElement {
    val nextHead = decoder.peek()
    return when(tag) {
      TDATE -> TDateElement(Instant.parse(decoder.decodeString()))
      TIME -> when(nextHead) {
        NEXT_HALF, NEXT_FLOAT -> DateTimeElement(Instant.fromEpochMilliseconds((decoder.decodeFloat() * 1000.0f).toLong()), DEDateTimeMode.time_float)
        NEXT_DOUBLE -> DateTimeElement(Instant.fromEpochMilliseconds((decoder.decodeDouble() * 1000.0).toLong()), DEDateTimeMode.time_double)
        else -> DateTimeElement(Instant.fromEpochSeconds(decoder.decodeLong()), DEDateTimeMode.time_int)
      }
      else -> throw SerializationException("Unsupported tag number for DateTime value: #6.$tag, supported tags are #6.0, #6.1")
    }

  }

  private fun serializeFullDate(encoder: Encoder, value: FullDateElement) {
    val attribute = value.attribute as DEFullDateAttribute
    when(attribute.mode) {
      DEFullDateMode.full_date_str -> {
        encoder.encodeTag(FULL_DATE_STR.toULong())
        encoder.encodeString(value.value.toString())
      }
      DEFullDateMode.full_date_int -> {
        encoder.encodeTag(FULL_DATE_INT.toULong())
        encoder.encodeInt(value.value.toEpochDays())
      }
    }
  }

  private fun deserializeFullDate(decoder: Decoder, tag: Long): FullDateElement {
    return when(tag) {
      FULL_DATE_STR -> FullDateElement(LocalDate.parse(decoder.decodeString()), DEFullDateMode.full_date_str)
      FULL_DATE_INT -> FullDateElement(LocalDate.fromEpochDays(decoder.decodeLong().toInt()), DEFullDateMode.full_date_int)
      else -> throw SerializationException("Unsupported tag number for full-date: #6.$tag, supported tags are #6.1004, #6.100")
    }
  }

}