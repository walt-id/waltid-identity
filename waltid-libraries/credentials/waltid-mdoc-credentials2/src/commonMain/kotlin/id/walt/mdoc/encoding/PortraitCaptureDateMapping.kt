package id.walt.mdoc.encoding

import id.walt.mdoc.credsdata.MdocNamespaces
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Distinguishes an unrelated field from an explicitly absent portrait capture value. */
sealed interface PortraitCaptureDateMapping {
    data object NotApplicable : PortraitCaptureDateMapping
    data object Omit : PortraitCaptureDateMapping

    @OptIn(ExperimentalSerializationApi::class)
    class Mapped internal constructor(val value: CborString) : PortraitCaptureDateMapping
}

/**
 * Maps standard portrait capture values for issuer2 without rewriting saved profiles or sessions.
 * Legacy date-only inputs mean midnight UTC; supplied timestamps retain their instant.
 * Matching follows the namespace and element definition, independently of document type.
 * Explicit null is terminal omission; callers must not fall through to a configured mapper.
 */
@OptIn(ExperimentalSerializationApi::class)
fun mapPortraitCaptureDate(namespace: String, elementIdentifier: String, value: JsonElement): PortraitCaptureDateMapping {
    if (elementIdentifier != "portrait_capture_date" || namespace !in portraitNamespaces) return PortraitCaptureDateMapping.NotApplicable
    if (value == JsonNull) return PortraitCaptureDateMapping.Omit
    require(value is JsonPrimitive && value.isString) { "portrait_capture_date must be a date or timestamp string" }
    return PortraitCaptureDateMapping.Mapped(PortraitCaptureValue.parse(value.content).toCborString())
}

// PHOTO_ID is retained as a legacy portrait-field alias; current Photo ID models use PERSON.
private val portraitNamespaces = setOf(MdocNamespaces.MDL, MdocNamespaces.PERSON, MdocNamespaces.PHOTO_ID)
