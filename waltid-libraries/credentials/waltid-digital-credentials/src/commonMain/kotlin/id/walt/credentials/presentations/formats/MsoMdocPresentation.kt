package id.walt.credentials.presentations.formats

import id.walt.credentials.presentations.PresentationFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an ISO mdoc (mobile document) presentation.
 * The presentation is a single base64url-encoded string representing the
 * DeviceResponse CBOR structure.
 */
@Serializable
@SerialName("mso_mdoc")
data class MsoMdocPresentation(
    val deviceResponse: String
) : VerifiablePresentation(format = PresentationFormat.mso_mdoc)
