package id.walt.credentials.signatures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("signature-jwt")
data class JwtCredentialSignature(
    val signature: String,
    override val jwtHeader: JsonObject?
) : CredentialSignature(), JwtBasedSignature
