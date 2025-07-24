package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import id.walt.crypto.keys.Key
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("vc-mdocs")
data class MdocsCredential(
    override val signature: CredentialSignature?,
    override val signed: String?, override val credentialData: JsonObject,
    val docType: String,

    @EncodeDefault
    override var issuer: String? = null,
    @EncodeDefault
    override var subject: String? = null,
) : DigitalCredential() {
    override val format: String = "mso_mdoc"

    override suspend fun verify(publicKey: Key): Result<JsonElement> {
        TODO("Not yet implemented: verify mdocs")
    }
}
