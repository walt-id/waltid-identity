package id.walt.credentials.formats

import id.walt.cose.CoseSign1
import id.walt.cose.toCoseVerifier
import id.walt.cose.verify
import id.walt.credentials.signatures.*
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
sealed class AbstractW3C(
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {

    // TODO: issuer should move into signature, just use credential.signerKey from superclass
    @Deprecated("Use getSignerCrypto2Key()")
    override suspend fun getSignerKey(): Key? =
        when (signature) {
            null -> null
            is JwtBasedSignature -> (signature as JwtBasedSignature).getJwtBasedIssuer(credentialData)
            is CoseCredentialSignature -> (signature as CoseCredentialSignature).signerKey.key
            else -> throw NotImplementedError("Not yet implemented: Retrieve issuer key from SdJwtCredential with ${signature!!::class.simpleName} signature")
        }

    override suspend fun getSignerCrypto2Key(): Crypto2Key? = when (signature) {
        null -> null
        is JwtBasedSignature -> JwsSignatureScheme().getIssuerCrypto2KeyInfo(
            requireNotNull(signed) { "Cannot resolve signer key for unsigned credential" }
        ).resolved.key
        is CoseCredentialSignature -> documentSignerCrypto2Key()
        else -> throw UnsupportedOperationException("Crypto2 signer-key resolution is not implemented for ${signature!!::class.simpleName}")
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

    override suspend fun getHolderCrypto2Key(): Crypto2Key? = credentialData.resolveHolderCrypto2Key()

    init {
        selfCheck()
    }

    @Deprecated("Use verifyCrypto2(publicKey, allowedAlgorithms)")
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

    override suspend fun verifyCrypto2(
        publicKey: Crypto2Key,
        allowedAlgorithms: Crypto2VerificationAlgorithms,
    ): Result<JsonElement> = when (signature) {
        is JwtCredentialSignature, is SdJwtCredentialSignature -> {
            require(signed != null) { "Cannot verify unsigned credential" }
            require(allowedAlgorithms.jws.isNotEmpty()) { "W3C JWT verification requires an explicit JWS allowlist" }
            JwsSignatureScheme().verifyCrypto2(signed!!, publicKey, allowedAlgorithms.jws)
        }
        is CoseCredentialSignature -> crypto2Result {
            require(signed != null) { "Cannot verify unsigned COSE credential" }
            require(allowedAlgorithms.cose.isNotEmpty()) { "W3C COSE verification requires an explicit COSE allowlist" }
            val coseSign1 = CoseSign1.fromTagged(signed!!.decodeFromBase64Url())
            require(coseSign1.verify(publicKey, allowedAlgorithms.cose)) {
                "COSE_Sign1 signature on W3C credential failed to verify"
            }
            credentialData
        }
        is DataIntegrityProofCredentialSignature -> Result.failure(
            UnsupportedOperationException("Data Integrity Proof verification is not implemented")
        )
        null -> Result.failure(IllegalArgumentException("Credential contains no signature, cannot verify"))
    }

    private suspend fun documentSignerCrypto2Key(): Crypto2Key? {
        val coseSignature = signature as? CoseCredentialSignature ?: return null
        val x5c = coseSignature.x5cList?.x5c ?: return null
        return Crypto2JwtKeyResolver().resolveFromJwt(
            jwtHeader = buildJsonObject {
                put("x5c", JsonArray(x5c.map { JsonPrimitive(it.base64Der) }))
            },
            jwtPayload = credentialData,
        )?.key
    }
}
