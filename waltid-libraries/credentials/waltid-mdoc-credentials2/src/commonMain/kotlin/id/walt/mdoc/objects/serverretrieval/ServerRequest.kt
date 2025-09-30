package id.walt.mdoc.objects.serverretrieval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the top-level request sent from an mdoc reader to an Issuing Authority's
 * server infrastructure during a server retrieval flow. This is typically sent as a JSON object.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.2.2.1 (Server retrieval mdoc request)
 *
 * @property version The version of the `ServerRequest` structure (e.g., "1.0").
 * @property token The `server retrieval token` that the mdoc reader obtained from the mdoc during the
 * engagement phase. This token identifies the mdoc holder and the specific transaction
 * to the issuing authority's server.
 * @property docRequests A list containing one or more [ServerItemsRequest] objects, each specifying a
 * document and the data elements requested from it.
 */
@Serializable
data class ServerRequest(
    @SerialName("version")
    val version: String,

    @SerialName("token")
    val token: String,

    @SerialName("docRequests")
    val docRequests: List<ServerItemsRequest>
)
