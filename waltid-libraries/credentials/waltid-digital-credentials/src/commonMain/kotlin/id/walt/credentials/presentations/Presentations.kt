package id.walt.credentials.presentations

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

/**
 * A sealed interface representing the different Verifiable Presentation formats
 * supported by the OpenID4VP specification.
 * The "format" property is used to distinguish between types during serialization.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("format")
sealed interface PresentationFormat

/**
 * Represents a W3C Verifiable Presentation presented as a single JWT string.
 * The JWT payload itself contains the 'vp' object with the credential(s).
 */
@Serializable
@SerialName("jwt_vc_json")
data class JwtVcJsonPresentation(
    val jwt: String
) : PresentationFormat

/**
 * Represents an IETF SD-JWT Verifiable Credential presentation.
 * This is a single string composed of the SD-JWT, disclosures, and a Key-Binding JWT,
 * separated by '~'.
 */
@Serializable
@SerialName("dc+sd-jwt")
data class DcSdJwtPresentation(
    /** The core, issuer-signed SD-JWT part of the presentation. */
    val sdJwt: String,

    /** A list of the base64url-encoded disclosure strings being presented. */
    val disclosures: List<String> = emptyList(),

    /** The holder-signed Key-Binding JWT that proves possession and binds to the transaction. */
    val keyBindingJwt: String
) : PresentationFormat

/**
 * Represents a W3C Verifiable Presentation using Data Integrity and JSON-LD.
 * This presentation is a JSON object, not an encoded string.
 * It is stored here as a flexible [JsonObject].
 */
@Serializable
@SerialName("ldp_vc")
data class LdpVcPresentation(
    val presentation: JsonObject
) : PresentationFormat

/**
 * Represents an ISO mdoc (mobile document) presentation.
 * The presentation is a single base64url-encoded string representing the
 * DeviceResponse CBOR structure.
 */
@Serializable
@SerialName("mso_mdoc")
data class MsoMdocPresentation(
    val deviceResponse: String
) : PresentationFormat
