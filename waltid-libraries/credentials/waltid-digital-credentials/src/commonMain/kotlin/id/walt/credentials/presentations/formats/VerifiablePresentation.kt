package id.walt.credentials.presentations.formats

import id.walt.credentials.presentations.PresentationFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * A sealed interface representing the different Verifiable Presentation formats
 * supported by the OpenID4VP specification.
 * The "format" property is used to distinguish between types during serialization.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class VerifiablePresentation(val format: PresentationFormat) {
    //abstract suspend fun presentationVerification()
}
