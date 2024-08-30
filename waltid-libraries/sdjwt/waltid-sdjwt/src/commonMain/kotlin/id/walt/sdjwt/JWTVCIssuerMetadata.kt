package id.walt.sdjwt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class JWTVCIssuerMetadata(
    @SerialName("issuer") val issuer: String? = null,
    @SerialName("jwks") val jwks: JsonArray? = null,
    @SerialName("jwks_uri") val jwksUri: String? = null,
)