package id.walt.webwallet.service.exchange

import org.cose.java.OneKey
import com.nimbusds.jose.jwk.ECKey
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.OfferedCredential
import id.walt.oid4vc.data.ProofOfPossession
import id.walt.oid4vc.data.ProofType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

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
                ProofOfPossession.CWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    nonce = nonce,
                    coseKey = OneKey(
                        ECKey.parse(publicKey.getPublicKey().exportJWK()).toECPublicKey(),
                        null
                    ).AsCBOR().EncodeToBytes(),
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
