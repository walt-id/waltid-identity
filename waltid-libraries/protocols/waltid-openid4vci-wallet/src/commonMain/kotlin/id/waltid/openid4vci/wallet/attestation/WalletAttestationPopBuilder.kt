package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto.keys.Key as LegacyKey
import id.walt.crypto.keys.KeyType
import id.waltid.openid4vci.wallet.proof.ProofBuilderUtils
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

class WalletAttestationPopBuilder {

    @Deprecated("Use the Crypto2Key overload")
    suspend fun buildPopJwt(
        instanceKey: LegacyKey,
        clientId: String,
        audience: String,
    ): String {
        check(instanceKey.keyType == KeyType.secp256r1) {
            "Wallet attestation PoP requires a P-256 (secp256r1) key, got ${instanceKey.keyType}"
        }

        val now = ProofBuilderUtils.currentTimestampSeconds()
        val header = buildJsonObject {
            put("typ", "oauth-client-attestation-pop+jwt")
            put("alg", "ES256")
        }
        val payload = buildJsonObject {
            put("iss", clientId)
            put("aud", audience)
            put("iat", now)
            put("exp", now + 300)
            put("jti", Uuid.random().toString())
        }
        return instanceKey.signJws(payload.toString().encodeToByteArray(), header)
    }

    suspend fun buildPopJwt(
        instanceKey: Key,
        clientId: String,
        audience: String,
    ): String {
        check(instanceKey.spec == KeySpec.Ec(EcCurve.P256)) {
            "Wallet attestation PoP requires a P-256 key, got ${instanceKey.spec}"
        }
        requireNotNull(instanceKey.capabilities.signer) { "Wallet attestation PoP key does not support signing" }

        val now = ProofBuilderUtils.currentTimestampSeconds()

        val header = buildJsonObject {
            put("typ", "oauth-client-attestation-pop+jwt")
        }

        val payload = buildJsonObject {
            put("iss", clientId)
            put("aud", audience)
            put("iat", now)
            put("exp", now + 300)
            put("jti", Uuid.random().toString())
        }

        return CompactJws.sign(
            payload = payload.toString().encodeToByteArray(),
            key = instanceKey,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = header,
        )
    }
}
