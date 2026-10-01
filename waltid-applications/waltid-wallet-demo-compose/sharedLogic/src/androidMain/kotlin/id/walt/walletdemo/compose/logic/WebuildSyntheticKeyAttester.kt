package id.walt.walletdemo.compose.logic

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

internal const val WEBUILD_DEMO_PACKAGE = "id.walt.walletdemo.compose"

/** Demo-only PoC. Never attach synthetic assurance to a non-debuggable app. */
internal suspend fun syntheticWebuildAttesterFor(
    packageName: String,
    isDebuggable: Boolean,
): KeyAttestationProvider? {
    if (packageName != WEBUILD_DEMO_PACKAGE || !isDebuggable) return null
    return WebuildSyntheticKeyAttester(
        CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                KeyId("webuild-test-attester"),
                KeySpec.Ec(EcCurve.P256),
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        ),
    )
}

/** ISO assurance labels are simulated. This is not certified key or user-authentication evidence. */
private class WebuildSyntheticKeyAttester(
    override val verificationKey: Key,
) : KeyAttestationProvider {
    override suspend fun attest(request: KeyAttestationRequest): String {
        require(request.credentialIssuer == "https://issuer.devel.authologic.com") {
            "Synthetic WE BUILD attestation is restricted to the test issuer"
        }
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
            put("key_storage", buildJsonArray { add(JsonPrimitive("iso_18045_basic")) })
            put("user_authentication", buildJsonArray { add(JsonPrimitive("iso_18045_basic")) })
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
