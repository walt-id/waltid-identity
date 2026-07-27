package id.walt.credentials.formats

import id.walt.credentials.signatures.*
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("vc-w3c_1_1")
data class W3C11(
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

    override val signed: String?
) : AbstractW3C() {

    override val format: String = when (signature) {
        // A W3C VCDM credential secured with SD-JWT keeps the W3C DCQL/OID4VCI format identifier:
        // issuers advertise it as jwt_vc_json, so verifiers must be able to query it as jwt_vc_json.
        // `dc+sd-jwt` is reserved for IETF SD-JWT VC (which requires a `vct`).
        is JwtCredentialSignature, is SdJwtCredentialSignature -> "jwt_vc_json"
        is DataIntegrityProofCredentialSignature -> "ldp_vc"
        is CoseCredentialSignature -> "vc+cose"  // W3C VCDM secured with COSE_Sign1 (vc-jose-cose)
        null -> "unsigned"
    }
}
