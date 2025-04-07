package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class MdocsCredential(
    override val signature: CredentialSignature?,
    override val signed: String?, override val credentialData: JsonObject
) : DigitalCredential()
