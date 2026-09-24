package id.walt.itb

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.toPublicJwk
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/** ITB-only fixture. Its security, certification and status claims are simulated, not assessed. */
internal class ItbSyntheticKeyAttester(
    override val verificationKey: Key,
) : KeyAttestationProvider {
    override suspend fun attest(request: KeyAttestationRequest): String {
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        val attesterJwk = requireNotNull(verificationKey.capabilities.publicKeyExporter)
            .exportPublicKey().toPublicJwk(verificationKey.spec)
        val header = buildJsonObject {
            put("typ", "key-attestation+jwt")
            put("jwk", Json.parseToJsonElement(attesterJwk.data.toByteArray().decodeToString()))
        }
        val payload = buildJsonObject {
            put("iat", now)
            put("exp", now + 300)
            request.nonce?.let { put("nonce", it) }
            put("attested_keys", buildJsonArray {
                add(Json.parseToJsonElement(request.proofKey.data.toByteArray().decodeToString()))
            })
            put("key_storage", buildJsonArray { add(JsonPrimitive("https://example.invalid/walt-id/itb/key-storage-unassessed")) })
            put("user_authentication", buildJsonArray { add(JsonPrimitive("https://example.invalid/walt-id/itb/user-authentication-unassessed")) })
            put("certification", "https://example.invalid/walt-id/itb/no-certification")
            put("key_storage_status", buildJsonObject {
                put("status", buildJsonObject {
                    put("status_list", buildJsonObject {
                        put("uri", "https://example.invalid/walt-id/itb/no-status-list")
                        put("idx", 0)
                    })
                })
                put("exp", now + 3600)
            })
        }
        return CompactJws.sign(payload.toString().encodeToByteArray(), verificationKey, JwsAlgorithm.ES256, header)
    }
}
