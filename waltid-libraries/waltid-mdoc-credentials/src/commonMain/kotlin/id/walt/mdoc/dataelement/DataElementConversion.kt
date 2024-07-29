package id.walt.mdoc.dataelement

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Convert to CBOR data element
 */
fun Number.toDE() = NumberElement(this)
/**
 * Convert to CBOR data element
 */
fun UInt.toDE() = NumberElement(this)
/**
 * Convert to CBOR data element
 */
fun Float.toDE() = NumberElement(this)
/**
 * Convert to CBOR data element
 */
fun String.toDE() = StringElement(this)
/**
 * Convert to CBOR data element
 */
fun Boolean.toDE() = BooleanElement(this)
/**
 * Convert to CBOR data element
 */
fun ByteArray.toDE() = ByteStringElement(this)
/**
 * Convert to CBOR data element
 */
fun List<AnyDataElement>.toDE() = ListElement(this)
/**
 * Convert to CBOR data element
 */
fun <KT> Map<KT, AnyDataElement>.toDE() = MapElement(this.mapKeys { when(it.key) {
  is String -> MapKey(it.key as String)
  is Int -> MapKey(it.key as Int)
  is MapKey -> it.key as MapKey
  else -> throw Exception("Unsupported map key type")
} })
/**
 * Convert to CBOR data element
 */
fun Instant.toDE(subType: DEDateTimeMode = DEDateTimeMode.tdate) = when(subType) {
  DEDateTimeMode.tdate -> TDateElement(this)
  else -> DateTimeElement(this, subType)
}
/**
 * Convert to CBOR data element
 */
fun LocalDate.toDE(subType: DEFullDateMode = DEFullDateMode.full_date_str) = FullDateElement(this, subType)
