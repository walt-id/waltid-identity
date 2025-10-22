package id.walt.credentials.formats

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireSuccess
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationThrowError
import id.walt.credentials.signatures.*
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("vc-sd_jwt")
data class SdJwtCredential(
    val dmtype: CredentialDetectorTypes.SDJWTVCSubType,

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
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {
    override val format: String = "dc+sd-jwt"

    // TODO: issuer should move into signature, just use credential.signerKey from superclass
    override suspend fun getSignerKey(): Key? =
        when (signature) {
            null -> null
            is JwtBasedSignature -> signature.getJwtBasedIssuer(credentialData)
            else -> throw NotImplementedError("Not yet implemented: Retrieve issuer key from SdJwtCredential with ${signature::class.simpleName} signature")
        }

    suspend fun getHolderKey(): Key? {
        val cnf = credentialData["cnf"]?.jsonObject
            ?: return null

        val key: Key = when {
            cnf.contains("jwk") -> {
                val cnfJwk = cnf["jwk"]!!.jsonObject
                val jwkKeyImportRes = JWKKey.importJWK(cnfJwk.toString())
                val jwkKey = presentationRequireSuccess(jwkKeyImportRes, DcSdJwtPresentationValidationError.CNF_JWK_CANNOT_PARSE_JWK)
                jwkKey
            }

            cnf.contains("kid") -> {
                val holderKidRes = runCatching { cnf!!["kid"]!!.jsonPrimitive.content }
                val holderKid = presentationRequireSuccess(holderKidRes, DcSdJwtPresentationValidationError.INVALID_CNF_KID)
                val resolvedKey = presentationRequireSuccess(
                    DidService.resolveToKey(holderKid),
                    DcSdJwtPresentationValidationError.CNF_KID_CANNOT_RESOLVE_DID
                )
                resolvedKey
            }

            else -> {
                presentationThrowError(DcSdJwtPresentationValidationError.MISSING_CNF_METHOD)
            }
        }

        return key
    }

    init {
        selfCheck()
    }

    override suspend fun verify(publicKey: Key) =
        when (signature) {
            is JwtCredentialSignature, is SdJwtCredentialSignature -> {
                require(signed != null) { "Cannot verify unsigned credential" }
                publicKey.verifyJws(signed)
            }

            is CoseCredentialSignature -> TODO("Not implemented yet: verify SD-JWT with COSE")
            is DataIntegrityProofCredentialSignature -> TODO("Not implemented yet: verify SD-JWT with DIP")
            null -> throw IllegalArgumentException("Credential contains no signature, cannot verify")
        }
}
