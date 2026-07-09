package id.walt.wallet2.mobile

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.WalletSessionEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobileWalletTest {

    @Test
    fun mobileWalletConfigUsesStableSdkDefaults() {
        val config = MobileWalletConfig()

        assertEquals("default", config.walletId)
        assertEquals(MobileWalletKeyType.secp256r1, config.defaultKeyType)
        assertEquals(null, config.attestationConfig)
    }

    @Test
    fun mobileWalletKeyTypeMapsToCryptoKeyTypeInternally() {
        assertEquals(KeyType.Ed25519, MobileWalletKeyType.Ed25519.toKeyType())
        assertEquals(KeyType.secp256k1, MobileWalletKeyType.secp256k1.toKeyType())
        assertEquals(KeyType.secp256r1, MobileWalletKeyType.secp256r1.toKeyType())
        assertEquals(KeyType.secp384r1, MobileWalletKeyType.secp384r1.toKeyType())
        assertEquals(KeyType.secp521r1, MobileWalletKeyType.secp521r1.toKeyType())
        assertEquals(KeyType.RSA, MobileWalletKeyType.RSA.toKeyType())
        assertEquals(KeyType.RSA3072, MobileWalletKeyType.RSA3072.toKeyType())
        assertEquals(KeyType.RSA4096, MobileWalletKeyType.RSA4096.toKeyType())
    }

    @Test
    fun walletSessionEventsMapToMobileWalletEventsInCommonCode() {
        val progress = WalletSessionEvent.issuance_offer_resolved.toMobileWalletEvent()
        val completed = WalletSessionEvent.presentation_completed.toMobileWalletEvent()
        val failed = WalletSessionEvent.issuance_failed.toMobileWalletEvent()

        assertEquals(MobileWalletEventPhase.issuance, progress.phase)
        assertEquals(MobileWalletEventStatus.progress, progress.status)
        assertEquals("issuance_offer_resolved", progress.name)

        assertEquals(MobileWalletEventPhase.presentation, completed.phase)
        assertEquals(MobileWalletEventStatus.completed, completed.status)
        assertEquals("presentation_completed", completed.name)

        assertEquals(MobileWalletEventPhase.issuance, failed.phase)
        assertEquals(MobileWalletEventStatus.failed, failed.status)
        assertEquals("issuance_failed", failed.name)
    }

    @Test
    fun presentationResultCarriesVerifierResponseAsJsonString() {
        val result = MobileWalletPresentationResult(
            success = true,
            redirectTo = "wallet://return",
            verifierResponseJson = """{"accepted":true}""",
        )

        assertEquals("""{"accepted":true}""", result.verifierResponseJson)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mobileWalletEventStreamDoesNotBackpressureSlowCollectors() = runTest {
        val stream = MobileWalletEventStream(replay = 1, extraBufferCapacity = 1)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            stream.events.collect {
                delay(Long.MAX_VALUE)
            }
        }

        runCurrent()

        repeat(100) { index ->
            val emitted = stream.tryEmit(progressEvent("issuance_progress_$index"))

            assertTrue(emitted, "Progress event $index should not suspend or fail when the buffer is full")
        }

        collector.cancel()
    }

    private fun progressEvent(name: String) = MobileWalletEvent(
        name = name,
        phase = MobileWalletEventPhase.issuance,
        status = MobileWalletEventStatus.progress,
    )
}
