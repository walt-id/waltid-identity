package id.walt.wallet2.mobile

import kotlin.time.TimeMark
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.wallet2.persistence.keys.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.uuid.Uuid
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

/** Timing/cancellation controls around the real provider; all authentication and signing stay native. */
internal suspend fun exerciseTimedNativeSca(
    provider: PlatformManagedKeyProvider,
    beforeStep: suspend (String) -> Unit,
): String = coroutineScope {
    val requirements = WalletKeyRequirements(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        KeyUseAuthorizationPolicy.BiometricTimedReuse(10), WalletKeyProtection.HardwareRequired)
    check(provider.preflight(requirements) is KeyUseAuthorizationSupport.Supported)
    val generated = provider.generateManagedKey(WalletKeyCreationRequest(KeyId("sca-timed-${Uuid.random()}"), requirements,
        KeyUseAuthorizationPrompt("Approve this synthetic payment")))
    try {
        val restored = (provider.restoreManagedKey(generated.storedKey) as PlatformManagedKeyRestoration.Restored).key
        check(provider.keyUseAuthorizationPolicy(restored.storedKey) == KeyUseAuthorizationPolicy.BiometricTimedReuse(10))
        var delaySigning = false
        var pauseSigning = false
        val authorized = CompletableDeferred<Unit>()
        var approvals = 0
        var signatures = 0
        var lastApproval: TimeMark? = null
        val observed = object : PlatformManagedKeyProvider by provider {
            override suspend fun <T> withFreshBiometricAuthorization(stored: StoredKey.Managed, operation: suspend (Signer) -> T): T {
                if (approvals == 1) check(requireNotNull(lastApproval).elapsedNow() < 10.seconds) {
                    "Second payment did not start inside the first approval's reuse window"
                }
                return provider.withFreshBiometricAuthorization(stored) { signer ->
                    approvals++
                    lastApproval = TimeSource.Monotonic.markNow()
                    if (delaySigning) delay(11_000)
                    if (pauseSigning) { authorized.complete(Unit); awaitCancellation() }
                    operation(Signer { data, algorithm -> signer.sign(data, algorithm).also { signatures++ } })
                }
            }
        }
        val fixture = scaWalletFixture(restored, observed)
        suspend fun pay() {
            val preview = fixture.preview()
            val response = fixture.submit(preview)
            val vp = Json.parseToJsonElement(response.dataJson).jsonObject.getValue("vp_token")
                .jsonObject.getValue("payment").jsonArray.single().jsonPrimitive.content
            val proof = CompactJws.verify(vp.substringAfterLast('~'), restored, JwsAlgorithm.ES256)
            val claims = Json.parseToJsonElement(proof.payload.decodeToString()).jsonObject
            check(claims["aud"]?.jsonPrimitive?.content == "origin:https://verifier.example")
            check(claims["amr"] == buildJsonArray {
                add(buildJsonObject { put("possession", "other") })
                add(buildJsonObject { put("inherence", "other") })
            })
            check(claims.getValue("transaction_data_hashes").jsonArray.size == 1)
        }
        beforeStep("PAYMENTS_1_AND_2: approve TWO successive biometric prompts")
        pay()
        pay()
        check(approvals == 2 && signatures == 2)

        beforeStep("EXPIRY: approve, then wait 11 seconds; no second prompt should appear")
        delaySigning = true
        val expired = runCatching { pay() }.exceptionOrNull()
        delaySigning = false
        check(expired is KeyUseAuthorizationException && expired.failure == KeyUseAuthorizationFailure.AuthorizationNotCompleted) {
            "Expected expired production authorization, got $expired"
        }
        check(approvals == 3 && signatures == 2)

        beforeStep("CANCEL_BIOMETRIC: tap Cancel on the biometric prompt")
        val cancelled = runCatching { pay() }.exceptionOrNull()
        check(cancelled is KeyUseAuthorizationException && cancelled.failure == KeyUseAuthorizationFailure.AuthorizationNotCompleted) {
            "Expected cancelled biometric authorization, got $cancelled"
        }
        check(approvals == 3 && signatures == 2)

        beforeStep("CANCEL_REVIEW: the retained payment review is discarded without authenticating")
        val discarded = fixture.preview()
        fixture.wallet.discardDigitalCredentialPreview(discarded.requestId)
        check(runCatching { fixture.submit(discarded) }.isFailure)
        check(approvals == 3 && signatures == 2)

        beforeStep("CANCEL_AFTER_APPROVAL: approve biometrics; the pending payment is then cancelled")
        pauseSigning = true
        val pending = fixture.preview()
        val submission = async { runCatching { fixture.submit(pending) } }
        withTimeout(120_000) { authorized.await() }
        fixture.wallet.discardDigitalCredentialPreview(pending.requestId)
        check(submission.await().isFailure)
        pauseSigning = false
        check(approvals == 4 && signatures == 2)

        beforeStep("RETRY: approve a new payment")
        pay()
        check(approvals == 5 && signatures == 3)
        "PASS timed-production-path; fresh-approvals=5; verified-payments=3; expiry; biometric-cancel; review-cancel; in-flight-cancel; retry"
    } finally {
        withContext(NonCancellable) { provider.deleteManagedKey(generated.storedKey) }
    }
}
