package id.walt.credentials.presentations.formats

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.presentations.PresentationFormat
import id.walt.crypto.utils.Base64Utils.matchesBase64
import id.walt.crypto.utils.Base64Utils.matchesBase64Url
import id.walt.crypto.utils.HexUtils.matchesHex
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
) : VerifiablePresentation(format = PresentationFormat.mso_mdoc) {

    companion object {
        suspend fun parse(mdocString: String): Result<MsoMdocPresentation> {
            val isHex = mdocString.matchesHex()
            val isBase64 = mdocString.matchesBase64Url() || mdocString.matchesBase64()

            return runCatching {
                val (_, mdocsCredential) = when {
                    isHex -> CredentialParser.handleMdocs(mdocString, base64 = false)
                    isBase64 -> CredentialParser.handleMdocs(mdocString, base64 = true)
                    else -> throw IllegalArgumentException("Invalid mdoc encoding: $mdocString")
                }

                MsoMdocPresentation(mdocsCredential)
            }
        }
    }
}
