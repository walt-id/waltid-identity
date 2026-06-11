package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.waltid.openid4vci.wallet.proof.ProofBuilderUtils
import io.ktor.utils.io.core.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WalletAttestationPopBuilder {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun buildPopJwt(
        instanceKey: Key,
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

        return instanceKey.signJws(
            payload.toString().toByteArray(),
            header.toJsonElement().jsonObject,
        )
    }
}
