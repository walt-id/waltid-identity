package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("vc-mdocs")
data class MdocsCredential(
    override val signature: CredentialSignature?,
    override val signed: String?, override val credentialData: JsonObject
) : DigitalCredential()
