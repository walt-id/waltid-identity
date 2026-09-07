@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.EngagementContext
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.MdocEngagementMode
import id.walt.mdoc.proximity.MdocProximityProfile
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ReaderSelectedTransportOffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WifiAwareTransportProviderTest {
    private val eDeviceKeyBytes = ImmutableBytes.of(ByteArray(32) { it.toByte() })
    private val qrContext = EngagementContext(
        profile = MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
        maximumMessageBytes = 1024,
        engagementMode = MdocEngagementMode.Qr,
    )

    @Test
    fun `capability preserves implemented platform failure`() = runTest {
        val platform = FakeWifiAwarePlatform(
            WifiAwareProximityAvailability.Unavailable(
                implemented = false,
                code = "wifi_aware_ios_api_unsupported",
                message = "Unsupported",
            )
        )

        val capability = provider(platform).capability(qrContext)

        assertFalse(capability.implemented)
        assertFalse(capability.runtimeAvailable)
        assertEquals("wifi_aware_ios_api_unsupported", capability.unavailableReason?.code)
    }

    @Test
    fun `reader offer rejects a preselected passphrase`() {
        val provider = provider(FakeWifiAwarePlatform())

        assertFalse(
            provider.acceptsReaderOffer(
                ReaderSelectedTransportOffer.Method(
                    DeviceRetrievalMethod.WifiAware(
                        passphraseInfo = "12345678",
                        supportedBands = byteArrayOf(0x04),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `QR and negotiated NFC own separate publications and transaction secrets`() = runTest {
        val platform = FakeWifiAwarePlatform()
        val provider = provider(platform)
        val qr = provider.prepare(qrContext, this)
        val nfcPlatform = FakeWifiAwarePlatform()
        val nfcKeyBytes = ImmutableBytes.of(ByteArray(32) { (it + 1).toByte() })
        val nfc = DefaultWifiAwareProximityTransportProvider(
            WifiAwareProximityTransportConfiguration(nfcKeyBytes), nfcPlatform,
        ).prepareReaderSelected(
            ReaderSelectedTransportOffer.Method(
                DeviceRetrievalMethod.WifiAware(
                    passphraseInfo = null,
                    supportedBands = byteArrayOf(0x04),
                )
            ),
            qrContext.copy(engagementMode = MdocEngagementMode.Nfc),
            this,
        )

        assertEquals(1, platform.prepareCount)
        assertEquals(WifiAwareProtocol.deriveServiceName(eDeviceKeyBytes), platform.serviceName)
        assertEquals(WifiAwareProtocol.derivePassphrase(eDeviceKeyBytes), platform.passphrase)
        assertNull((qr.connectionMethod as DeviceRetrievalMethod.WifiAware).passphraseInfo)
        val selected = nfc.connectionMethod as DeviceRetrievalMethod.WifiAware
        assertEquals(WifiAwareProtocol.derivePassphrase(nfcKeyBytes), selected.passphraseInfo)
        assertFalse(platform.serviceName == nfcPlatform.serviceName)
        assertContentEquals(byteArrayOf(0x04), selected.supportedBands)

        assertFailsWith<IllegalStateException> { provider.prepare(qrContext, this) }
        qr.close(ProximityCloseReason.CANCELLED)
        assertEquals(listOf(ProximityCloseReason.CANCELLED), platform.publisher.closeReasons)
        assertTrue(nfcPlatform.publisher.closeReasons.isEmpty())
        nfc.close(ProximityCloseReason.COMPLETED)
        assertEquals(listOf(ProximityCloseReason.COMPLETED), nfcPlatform.publisher.closeReasons)
    }

    private fun provider(platform: FakeWifiAwarePlatform) = DefaultWifiAwareProximityTransportProvider(
        WifiAwareProximityTransportConfiguration(eDeviceKeyBytes),
        platform,
    )
}

private class FakeWifiAwarePlatform(
    private val availability: WifiAwareProximityAvailability = WifiAwareProximityAvailability.Available,
) : WifiAwarePlatformAdapter {
    val publisher = FakeWifiAwarePublisher()
    var prepareCount = 0
    var serviceName: String? = null
    var passphrase: String? = null

    override suspend fun capability(): WifiAwareProximityAvailability =
        availability

    override suspend fun preparePublisher(
        serviceName: String,
        passphrase: String,
        sessionScope: CoroutineScope,
    ): WifiAwarePreparedPlatformPublisher {
        prepareCount++
        this.serviceName = serviceName
        this.passphrase = passphrase
        return publisher
    }
}

private class FakeWifiAwarePublisher : WifiAwarePreparedPlatformPublisher {
    override val supportedBands = WifiAwareSupportedBands.fromBytes(byteArrayOf(0x14))
    val closeReasons = mutableListOf<ProximityCloseReason>()

    override suspend fun awaitConnection(): WifiAwareRawConnection =
        error("Connection is not needed by this preparation test")

    override fun close(reason: ProximityCloseReason) {
        closeReasons += reason
    }
}
