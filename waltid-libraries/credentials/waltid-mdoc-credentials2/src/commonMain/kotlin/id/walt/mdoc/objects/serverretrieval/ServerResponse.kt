package id.walt.mdoc.objects.serverretrieval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the top-level response sent from an Issuing Authority's server infrastructure
 * to an mdoc reader during a server retrieval flow. This is typically sent as a JSON object.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.2.2.2 (Server retrieval mdoc response)
 *
 * @property version The version of the `ServerResponse` structure (e.g., "1.0").
 * @property documents A list of returned documents. Each document is a JSON Web Token (JWT)
 * protected by a JSON Web Signature (JWS), transmitted as a compact JWS string. The payload
 * of each JWT contains the actual credential data for one document.
 * @property documentErrors An optional map of document types to error codes for any requested
 * documents that could not be returned.
 */
@Serializable
data class ServerResponse(
    @SerialName("version")
    val version: String,
    /**
     * A single document is a [JwsSigned]
     */
    @SerialName("documents")
    val documents: List<String>,
    @SerialName("documentErrors")
    val documentErrors: Map<String, Int>? = null,
)
