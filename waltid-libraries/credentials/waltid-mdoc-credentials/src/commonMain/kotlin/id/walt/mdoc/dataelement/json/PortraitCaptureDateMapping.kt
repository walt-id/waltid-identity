package id.walt.mdoc.dataelement.json

import id.walt.mdoc.dataelement.TDateElement
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Corrects portrait capture values at issuance without migrating saved profiles or sessions.
 * Legacy dates mean midnight UTC; timestamps retain their instant. Unrelated fields are untouched.
 */
fun mapPortraitCaptureDate(namespace: String, elementIdentifier: String, value: JsonElement): TDateElement? {
    if (elementIdentifier != "portrait_capture_date" || namespace !in portraitNamespaces) return null
    require(value is JsonPrimitive && value.isString) { "portrait_capture_date must be a date or timestamp string" }
    val instant = if (value.content.length == 10) {
        LocalDate.parse(value.content).atStartOfDayIn(TimeZone.UTC)
    } else Instant.parse(value.content)
    val wholeSeconds = Instant.fromEpochSeconds(instant.epochSeconds)
    require(wholeSeconds.toString().length == 20) {
        "mdoc timestamps require a four-digit year"
    }
    return TDateElement(wholeSeconds)
}

private val portraitNamespaces = setOf("org.iso.18013.5.1", "org.iso.23220.1", "org.iso.23220.photoid.1")
