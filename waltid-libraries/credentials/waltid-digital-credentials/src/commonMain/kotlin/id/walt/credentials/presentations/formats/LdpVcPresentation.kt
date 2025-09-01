package id.walt.credentials.presentations.formats

import id.walt.credentials.presentations.PresentationFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a W3C Verifiable Presentation using Data Integrity and JSON-LD.
 * This presentation is a JSON object, not an encoded string.
 * It is stored here as a flexible [JsonObject].
 */
@Serializable
@SerialName("ldp_vc")
data class LdpVcPresentation(
    val presentation: JsonObject
) : VerifiablePresentation(format = PresentationFormat.ldp_vc)
