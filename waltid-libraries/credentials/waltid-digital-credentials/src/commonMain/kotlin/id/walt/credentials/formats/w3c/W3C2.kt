package id.walt.credentials.formats

import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.CredentialSignature
import id.walt.credentials.signatures.DataIntegrityProofCredentialSignature
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.credentials.signatures.SdJwtCredentialSignature
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("vc-w3c_2")
data class W3C2(
    // Selective disclosure
    override val disclosables: Map<String, Set<String>>? = null,
    override val disclosures: List<SdJwtSelectiveDisclosure>? = null,
    override val signedWithDisclosures: String? = null,

    // Data
    override val credentialData: JsonObject,
    override val originalCredentialData: JsonObject? = null,

    @EncodeDefault
    override var issuer: String? = null,
    @EncodeDefault
    override var subject: String? = null,

    // Signature
    override val signature: CredentialSignature?,
    override val signed: String?,
) : AbstractW3C() {

    override val format: String = when (signature) {
        is JwtCredentialSignature -> "jwt_vc_json"
        is SdJwtCredentialSignature -> "jwt_vc_json"
        is DataIntegrityProofCredentialSignature -> "ldp_vc"
        is CoseCredentialSignature -> throw NotImplementedError("Unsupported format: $signature of ${this::class.simpleName}")
        null -> "unsigned"
    }
}
