package id.walt.credentials.presentations.formats

import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.presentations.PresentationFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a Verifiable Presentation in the mso_mdoc format.
 * The core data is the MdocsCredential itself, which contains the
 * base64url-encoded DeviceResponse string.
 */
@Serializable
@SerialName("mso_mdoc")
data class MsoMdocPresentation(
    val mdoc: MdocsCredential
) : VerifiablePresentation(format = PresentationFormat.mso_mdoc)
