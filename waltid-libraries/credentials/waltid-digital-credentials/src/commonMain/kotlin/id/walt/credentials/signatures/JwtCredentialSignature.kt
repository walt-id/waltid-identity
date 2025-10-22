package id.walt.credentials.signatures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("signature-jwt")
data class JwtCredentialSignature(
    val signature: String,
    //override var signerKey: Key,
    override val jwtHeader: JsonObject?,
    //override var x5cList: X5CList? = null // TODO?
) : CredentialSignature(), JwtBasedSignature
