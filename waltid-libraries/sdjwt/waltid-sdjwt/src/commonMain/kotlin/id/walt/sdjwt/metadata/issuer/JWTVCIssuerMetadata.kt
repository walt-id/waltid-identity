package id.walt.sdjwt.metadata.issuer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class JWTVCIssuerMetadata(
    @SerialName("issuer") val issuer: String? = null,
    @SerialName("jwks") val jwks: JsonObject? = null,
    @SerialName("jwks_uri") val jwksUri: String? = null,
)