package id.walt.credentials.signatures

import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("signature-sd_jwt")
data class SdJwtCredentialSignature(
    val signature: String,
    override val jwtHeader: JsonObject?,
    val providedDisclosures: List<SdJwtSelectiveDisclosure>? = null,
    //override var signerKey: Key,
    //override var x5cList: X5CList? = null
) : CredentialSignature(), JwtBasedSignature
