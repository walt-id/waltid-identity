package id.walt.webwallet.service.exchange

import com.nimbusds.jose.jwk.ECKey
import id.walt.cose.Cose
import id.walt.cose.CoseKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.OfferedCredential
import id.walt.oid4vc.data.ProofOfPossession
import id.walt.oid4vc.data.ProofType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cose.java.OneKey

object ProofOfPossessionParameterFactory {
    suspend fun new(
        didAuthKeyId: String,
        publicKey: Key,
        useKeyProof: Boolean,
        offeredCredential: OfferedCredential,
        credentialOffer: CredentialOffer,
        nonce: String?
    ): ProofOfPossessionParameters = when (useKeyProof) {
        true -> keyProofParameters(publicKey, offeredCredential, credentialOffer, nonce)
        false -> didProofParameters(didAuthKeyId, offeredCredential, credentialOffer, nonce)
    }

    private suspend fun keyProofParameters(
        publicKey: Key,
        offeredCredential: OfferedCredential,
        credentialOffer: CredentialOffer,
        nonce: String?
    ): ProofOfPossessionParameters {
        val proofType = offeredCredential.proofTypesSupported?.keys?.first() ?: ProofType.jwt
        return when (proofType) {
            ProofType.cwt -> {
                // For CWT proofs, we need to use the old OneKey format because the issuer
                // still uses the old library's COSESign1Utils.extractHolderKey to parse it.
                // The new library is only used for mdoc document creation/verification, not for CWT proofs.
                val coseKey = OneKey(
                    ECKey.parse(publicKey.getPublicKey().exportJWK()).toECPublicKey(),
                    null
                ).AsCBOR().EncodeToBytes()
                
                ProofOfPossession.CWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    nonce = nonce,
                    coseKey = coseKey,
                ).let {
                    ProofOfPossessionParameters(
                        ProofType.cwt,
                        Json.encodeToJsonElement(it.headers.toCBOR().encodeToBase64()),
                        Json.encodeToJsonElement(it.payload.toCBOR().encodeToBase64()),
                    )
                }
            }

            ProofType.ldp_vp -> TODO("ldp_vp proof not yet implemented")
            else -> {
                ProofOfPossession.JWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    nonce = nonce,
                    keyJwk = publicKey.exportJWKObject(),
                ).let {
                    ProofOfPossessionParameters(
                        ProofType.jwt,
                        it.headers.toJsonElement(),
                        it.payload.toJsonElement(),
                    )
                }
            }
        }
    }

    private fun didProofParameters(
        didAuthKeyId: String,
        offeredCredential: OfferedCredential,
        credentialOffer: CredentialOffer,
        nonce: String?
    ): ProofOfPossessionParameters {
        val proofType = offeredCredential.proofTypesSupported?.keys?.first() ?: ProofType.jwt
        return when (proofType) {
            ProofType.cwt -> {
                ProofOfPossession.CWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    nonce = nonce,
                ).let {
                    ProofOfPossessionParameters(
                        ProofType.cwt,
                        Json.encodeToJsonElement(it.headers.toCBOR().encodeToBase64()),
                        Json.encodeToJsonElement(it.payload.toCBOR().encodeToBase64()),
                    )
                }
            }

            ProofType.ldp_vp -> TODO("ldp_vp proof not yet implemented")
            else -> {
                ProofOfPossession.JWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    nonce = nonce,
                    keyId = didAuthKeyId,
                ).let {
                    ProofOfPossessionParameters(
                        ProofType.jwt,
                        it.headers.toJsonElement(),
                        it.payload.toJsonElement(),
                    )
                }
            }
        }
    }
}

@Serializable
data class ProofOfPossessionParameters(
    val proofType: ProofType,
    val header: JsonElement,
    val payload: JsonElement,
)

/**
 * Converts a Key to CoseKey format compatible with the new mdocs2 library.
 */
@OptIn(ExperimentalSerializationApi::class)
private suspend fun Key.toCoseKey(): CoseKey {
    val jwk = exportJWKObject()
    val kty = jwk["kty"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("JWK missing kty")
    val crv = jwk["crv"]?.jsonPrimitive?.content
    
    // Map JWK key type to COSE key type
    val coseKty = when (kty) {
        "EC" -> Cose.KeyTypes.EC2
        "OKP" -> Cose.KeyTypes.OKP
        else -> throw IllegalArgumentException("Unsupported JWK key type: $kty")
    }
    
    // Map JWK curve to COSE curve
    val coseCrv = when (crv) {
        "P-256" -> Cose.EllipticCurves.P_256
        "P-384" -> Cose.EllipticCurves.P_384
        "P-521" -> Cose.EllipticCurves.P_521
        "Ed25519" -> Cose.EllipticCurves.Ed25519
        "Ed448" -> Cose.EllipticCurves.Ed448
        "secp256k1" -> Cose.EllipticCurves.secp256k1
        else -> throw IllegalArgumentException("Unsupported JWK curve: $crv")
    }
    
    // Extract x, y coordinates from JWK
    val x = jwk["x"]?.jsonPrimitive?.content?.base64UrlDecode()
        ?: throw IllegalArgumentException("JWK missing x coordinate")
    val y = if (coseKty == Cose.KeyTypes.EC2) {
        jwk["y"]?.jsonPrimitive?.content?.base64UrlDecode()
            ?: throw IllegalArgumentException("JWK missing y coordinate for EC key")
    } else null
    
    // Extract private key if present
    val d = jwk["d"]?.jsonPrimitive?.content?.base64UrlDecode()
    
    // Extract kid if present
    val kid = jwk["kid"]?.jsonPrimitive?.content?.encodeToByteArray()
    
    return CoseKey(
        kty = coseKty,
        kid = kid,
        crv = coseCrv,
        x = x,
        y = y,
        d = d
    )
}
