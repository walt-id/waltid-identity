package id.walt.credentials.formats

import id.walt.cose.CoseSign1
import id.walt.cose.toCoseVerifier
import id.walt.credentials.signatures.*
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject

@Serializable
sealed class AbstractW3C(
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {

    // TODO: issuer should move into signature, just use credential.signerKey from superclass
    override suspend fun getSignerKey(): Key? =
        when (signature) {
            null -> null
            is JwtBasedSignature -> (signature as JwtBasedSignature).getJwtBasedIssuer(credentialData)
            is CoseCredentialSignature -> (signature as CoseCredentialSignature).signerKey.key
            else -> throw NotImplementedError("Not yet implemented: Retrieve issuer key from SdJwtCredential with ${signature!!::class.simpleName} signature")
        }

    /**
     * Resolve holder's public key from the `cnf.jwk` claim, if present.
     * Used for W3C+SD-JWT credentials (vc-jose-cose) that embed a holder key.
     */
    override suspend fun getHolderKey(): Key? {
        val cnf = credentialData["cnf"]?.jsonObject ?: return null
        val jwk = cnf["jwk"]?.jsonObject ?: return null
        return JWKKey.importJWK(jwk.toString()).getOrNull()
    }

    init {
        selfCheck()
    }

    override suspend fun verify(publicKey: Key) =
        when (signature) {
            is JwtCredentialSignature, is SdJwtCredentialSignature -> {
                require(signed != null) { "Cannot verify unsigned credential" }
                publicKey.verifyJws(signed!!)
            }

            is CoseCredentialSignature -> {
                // W3C VCDM secured with COSE_Sign1 (vc-jose-cose §securing-vcs-with-cose).
                // The `signed` field holds the base64url-encoded COSE_Sign1 bytes.
                require(signed != null) { "Cannot verify unsigned COSE credential" }
                val coseBytes = signed!!.decodeFromBase64Url()
                val coseSign1 = CoseSign1.fromTagged(coseBytes)
                val valid = coseSign1.verify(publicKey.toCoseVerifier())
                require(valid) { "COSE_Sign1 signature on W3C credential failed to verify." }
                Result.success(credentialData)
            }

            is DataIntegrityProofCredentialSignature -> throw UnsupportedOperationException("Data Integrity Proof (LDP) signature verification for W3C credentials is not yet implemented")
            null -> throw IllegalArgumentException("Credential contains no signature, cannot verify")
        }
}

