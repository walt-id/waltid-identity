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
fun <KT> Map<KT, AnyDataElement>.toDataElement() = MapElement(this.mapKeys { when(it.key) {
  is String -> MapKey(it.key as String)
  is Int -> MapKey(it.key as Int)
  is MapKey -> it.key as MapKey
  else -> throw Exception("Unsupported map key type")
} })
/**
 * Convert to CBOR data element
 */
fun Instant.toDataElement(subType: DEDateTimeMode = DEDateTimeMode.tdate) = when(subType) {
  DEDateTimeMode.tdate -> TDateElement(this)
  else -> DateTimeElement(this, subType)
}
/**
 * Convert to CBOR data element
 */
fun LocalDate.toDataElement(subType: DEFullDateMode = DEFullDateMode.full_date_str) = FullDateElement(this, subType)

fun JsonElement.toDataElement(): AnyDataElement = when(this) {
  is JsonObject -> this.mapValues { it.value.toDataElement() }.toDataElement()
  is JsonArray -> this.map { it.toDataElement() }.toDataElement()
  is JsonPrimitive ->
    this.intOrNull?.toDataElement() ?:
    this.longOrNull?.toDataElement() ?:
    this.floatOrNull?.toDataElement() ?:
    this.doubleOrNull?.toDataElement() ?:
    this.booleanOrNull?.toDataElement() ?:
    this.content.toDataElement()
}

