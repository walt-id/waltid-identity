package id.walt.mdoc.encoding

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Maps standard portrait capture values for issuer2 without rewriting saved profiles or sessions.
 * Legacy date-only inputs mean midnight UTC; supplied timestamps retain their instant.
 */
@OptIn(ExperimentalSerializationApi::class)
fun mapPortraitCaptureDate(namespace: String, elementIdentifier: String, value: JsonElement): CborString? {
    if (elementIdentifier != "portrait_capture_date" || namespace !in portraitNamespaces) return null
    require(value is JsonPrimitive && value.isString) { "portrait_capture_date must be a date or timestamp string" }
    val instant = if (value.content.length == 10) {
        LocalDate.parse(value.content).atStartOfDayIn(TimeZone.UTC)
    } else Instant.parse(value.content)
    return CborString(instant.toMdocTDateString(), 0u)
}

private val portraitNamespaces = setOf("org.iso.18013.5.1", "org.iso.23220.1", "org.iso.23220.photoid.1")
