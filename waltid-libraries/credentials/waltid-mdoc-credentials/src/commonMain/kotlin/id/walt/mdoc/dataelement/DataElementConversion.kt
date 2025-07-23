package id.walt.mdoc.dataelement

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*

/**
 * Convert to CBOR data element
 */
fun Number.toDataElement() = NumberElement(this)

/**
 * Convert to CBOR data element
 */
fun UInt.toDataElement() = NumberElement(this)

/**
 * Convert to CBOR data element
 */
fun Float.toDataElement() = NumberElement(this)

/**
 * Convert to CBOR data element
 */
fun String.toDataElement() = StringElement(this)

/**
 * Convert to CBOR data element
 */
fun Boolean.toDataElement() = BooleanElement(this)

/**
 * Convert to CBOR data element
 */
fun ByteArray.toDataElement() = ByteStringElement(this)

/**
 * Convert to CBOR data element
 */
fun List<AnyDataElement>.toDataElement() = ListElement(this)

/**
 * Convert to CBOR data element
 */
fun <KT> Map<KT, AnyDataElement>.toDataElement() = MapElement(this.mapKeys {
    when (it.key) {
        is String -> MapKey(it.key as String)
        is Int -> MapKey(it.key as Int)
        is MapKey -> it.key as MapKey
        else -> throw Exception("Unsupported map key type")
    }
})

/**
 * Convert to CBOR data element
 */
fun Instant.toDataElement(subType: DEDateTimeMode = DEDateTimeMode.tdate) = when (subType) {
    DEDateTimeMode.tdate -> TDateElement(this)
    else -> DateTimeElement(this, subType)
}

/**
 * Convert to CBOR data element
 */
fun LocalDate.toDataElement(subType: DEFullDateMode = DEFullDateMode.full_date_str) = FullDateElement(this, subType)

fun JsonElement.toDataElement(): AnyDataElement = when (this) {
    is JsonObject -> this.mapValues { it.value.toDataElement() }.toDataElement()
    is JsonArray -> this.map { it.toDataElement() }.toDataElement()
    is JsonNull -> NullElement()
    is JsonPrimitive ->
        this.intOrNull?.toDataElement() ?: this.longOrNull?.toDataElement() ?: this.floatOrNull?.toDataElement()
        ?: this.doubleOrNull?.toDataElement() ?: this.booleanOrNull?.toDataElement() ?: this.content.toDataElement()
}

fun DataElement.toJsonElement(): JsonElement = when (this) {
    is NumberElement -> JsonPrimitive(this.value)
    is StringElement -> JsonPrimitive(this.value)
    is BooleanElement -> JsonPrimitive(this.value)
    is ByteStringElement -> JsonArray(this.value.map { JsonPrimitive(it) })
    is ListElement -> JsonArray(this.value.map { it.toJsonElement() })
    is MapElement -> JsonObject(this.value.mapKeys { it.key.toString() }.mapValues { it.value.toJsonElement() })
    is NullElement -> JsonNull
    is DateTimeElement -> JsonPrimitive(this.value.epochSeconds)
    is FullDateElement -> JsonPrimitive(this.value.toEpochDays() * 24 * 60 * 60)
    is EncodedCBORElement -> this.decode().toJsonElement()
    else -> throw Exception("Unsupported data type")
}

fun DataElement.toUIJson(): JsonElement = when (this) {
    is MapElement -> buildJsonObject {
        this@toUIJson.value.forEach { (key, value) ->
            when(key.type) {
                MapKeyType.int -> put(key.int.toString(), value.toUIJson())
                MapKeyType.string -> put(key.str, value.toUIJson())
            }
        }
    }

    is EncodedCBORElement -> this.decode().toUIJson()
    else -> this.toJsonElement()
}
