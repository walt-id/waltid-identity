package id.walt.wallet2.mobile

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.wallet2.persistence.keys.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.uuid.Uuid

/** Real native key, synthetic credential, and local signature verification; no issuer or verifier relaxation. */
internal suspend fun exerciseNativeSca(provider: PlatformManagedKeyProvider, cancelFirst: Boolean): String {
    val requirements = WalletKeyRequirements(
        KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        KeyUseAuthorizationPolicy.BiometricCurrentSet, WalletKeyProtection.HardwareRequired,
    )
    check(provider.preflight(requirements) is KeyUseAuthorizationSupport.Supported) {
        "Physical device must support a hardware key with strong, enrolled biometrics"
    }
    val request = WalletKeyCreationRequest(
        KeyId("wal1423-sca-test-${Uuid.random()}"), requirements,
        KeyUseAuthorizationPrompt("Authorize the WAL-1423 synthetic payment test"),
    )
    val generated = provider.generateManagedKey(request)
    try {
        // Exercise restoration and its native policy provenance, not just the freshly generated handle.
        val restored = provider.restoreManagedKey(generated.storedKey) as PlatformManagedKeyRestoration.Restored
        val fixture = scaWalletFixture(restored.key, provider)
        val preview = fixture.preview()
        if (cancelFirst) {
            val failure = runCatching { fixture.submit(preview) }.exceptionOrNull()
            check(failure is KeyUseAuthorizationException && failure.failure == KeyUseAuthorizationFailure.AuthorizationNotCompleted) {
                "Expected cancellation with no presentation response, got $failure"
            }
        }
        val response = fixture.submit(preview)
        val vp = Json.parseToJsonElement(response.dataJson).jsonObject.getValue("vp_token")
            .jsonObject.getValue("payment").jsonArray.single().jsonPrimitive.content
        val proof = CompactJws.verify(vp.substringAfterLast('~'), restored.key, JwsAlgorithm.ES256)
        val claims = Json.parseToJsonElement(proof.payload.decodeToString()).jsonObject
        check(claims["aud"]?.jsonPrimitive?.content == "origin:https://verifier.example")
        check(claims["response_mode"]?.jsonPrimitive?.content == "dc_api")
        check(claims["amr"] == buildJsonArray {
            add(buildJsonObject { put("possession", "other") })
            add(buildJsonObject { put("inherence", "other") })
        })
        check(!claims["jti"]?.jsonPrimitive?.content.isNullOrBlank())
        check(runCatching { fixture.submit(preview) }.isFailure) { "A completed preview must be single-use" }
        val facts = provider.keyFacts(restored.key.storedKey)
        return "PASS restored-key signed-payment; cancel-retry=$cancelFirst; backing=${facts.securityLevel}; evidence=${facts.authorizationEvidence}"
    } finally {
        withContext(NonCancellable) { provider.deleteManagedKey(generated.storedKey) }
    }
}
