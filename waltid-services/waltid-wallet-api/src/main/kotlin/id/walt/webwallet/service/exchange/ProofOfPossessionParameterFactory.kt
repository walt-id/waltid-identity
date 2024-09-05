package id.walt.webwallet.service.exchange

import COSE.OneKey
import com.nimbusds.jose.jwk.ECKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidService
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
        did: String,
        keyId: String,
        useKeyProof: Boolean,
        offeredCredential: OfferedCredential,
        credentialOffer: CredentialOffer,
        nonce: String?
    ): ProofOfPossessionParameters = when (useKeyProof) {
        true -> keyProofParameters(did, offeredCredential, credentialOffer, nonce)
        false -> didProofParameters(did, keyId, offeredCredential, credentialOffer, nonce)
    }

    private suspend fun keyProofParameters(
        did: String,
        offeredCredential: OfferedCredential,
        credentialOffer: CredentialOffer,
        nonce: String?
    ): ProofOfPossessionParameters {
        val key = DidService.resolveToKey(did).getOrThrow()
        val proofType = offeredCredential.proofTypesSupported?.keys?.first() ?: ProofType.jwt
        return when (proofType) {
            ProofType.cwt -> {
                ProofOfPossession.CWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    nonce = nonce,
                    coseKey = OneKey(
                        ECKey.parse(key.getPublicKey().exportJWK()).toECPublicKey(),
                        null
                    ).AsCBOR().EncodeToBytes(),
                ).let {
                    ProofOfPossessionParameters(
                        ProofType.cwt,
                        Json.encodeToJsonElement(it.headers.toCBOR()),
                        Json.encodeToJsonElement(it.payload.toCBOR()),
                    )
                }
            }

            ProofType.ldp_vp -> TODO("ldp_vp proof not yet implemented")
            else -> {
                ProofOfPossession.JWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    nonce = nonce,
                    keyJwk = key.getPublicKey().exportJWKObject(),
                    keyId = key.getKeyId(),
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
        did: String,
        keyId: String,
        offeredCredential: OfferedCredential,
        credentialOffer: CredentialOffer,
        nonce: String?
    ): ProofOfPossessionParameters {
        val proofType = offeredCredential.proofTypesSupported?.keys?.first() ?: ProofType.jwt
        return when (proofType) {
            ProofType.cwt -> {
                ProofOfPossession.CWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    clientId = did,
                    nonce = nonce,
                ).let {
                    ProofOfPossessionParameters(
                        ProofType.cwt,
                        Json.encodeToJsonElement(it.headers.toCBOR()),
                        Json.encodeToJsonElement(it.payload.toCBOR()),
                    )
                }
            }

            ProofType.ldp_vp -> TODO("ldp_vp proof not yet implemented")
            else -> {
                ProofOfPossession.JWTProofBuilder(
                    issuerUrl = credentialOffer.credentialIssuer,
                    clientId = did,
                    nonce,
                    keyId,
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
