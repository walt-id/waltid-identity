package id.walt.itb

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.toPublicJwk
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/** TODO [WAL-1423]: remove this test-only attester after a trusted Wallet Provider path is available. */
internal class ItbDiagnosticKeyAttester(
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
        }
        return CompactJws.sign(payload.toString().encodeToByteArray(), verificationKey, JwsAlgorithm.ES256, header)
    }
}
