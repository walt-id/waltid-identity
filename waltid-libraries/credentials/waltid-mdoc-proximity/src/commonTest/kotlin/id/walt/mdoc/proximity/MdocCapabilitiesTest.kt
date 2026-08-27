@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MdocCapabilitiesTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `profiles keep implementation permission runtime and selection independent`() = runTest {
        withContext(Dispatchers.Default) {
            val p256 = runtime.generateMdocTestKey("capability-p256", setOf(KeyUsage.KEY_AGREEMENT))
            val capabilities = MdocSessionCapabilities.forSession(
                MdocProximityProfile.EUDI_ARF_3_FCAF_2026_08,
                p256,
                setOf(MdocProtocolFeature.READER_AUTH_ALL),
            )

            assertEquals(MdocSessionCurve.P256, capabilities.selectedCurve)
            assertTrue(capabilities.curves.getValue(MdocSessionCurve.P256).available)
            assertTrue(capabilities.features.getValue(MdocProtocolFeature.READER_AUTH_ALL).sessionSelected)
            assertFalse(capabilities.curves.getValue(MdocSessionCurve.BRAINPOOL_P256R1).implemented)
            assertTrue(capabilities.curves.getValue(MdocSessionCurve.BRAINPOOL_P256R1).profilePermitted)
            assertFalse(capabilities.curves.getValue(MdocSessionCurve.X25519).profilePermitted)
            assertFalse(capabilities.curves.getValue(MdocSessionCurve.X25519).runtimeAvailable)

            val firstEdition = MdocSessionCapabilities.forSession(
                MdocProximityProfile.ISO_18013_5_2021,
                p256,
                emptySet(),
            )
            val engagement = MdocDeviceEngagementFactory().create(
                eDeviceKey = p256,
                methods = listOf(DeviceRetrievalMethod.Nfc(1_024u, 1_024u)),
                context = EngagementContext(firstEdition.profile, 1_048_576, MdocEngagementMode.Qr),
                capabilities = firstEdition,
            ).engagement.value
            assertEquals(DeviceEngagement.VERSION_1_0, engagement.version)
            assertEquals(null, engagement.originInfos)
            assertEquals(null, engagement.capabilities)
        }
    }

    @Test
    fun `session selection rejects capabilities forbidden by the active profile`() = runTest {
        withContext(Dispatchers.Default) {
            val p256 = runtime.generateMdocTestKey("capability-2021", setOf(KeyUsage.KEY_AGREEMENT))
            assertFailsWith<IllegalArgumentException> {
                MdocSessionCapabilities.forSession(
                    MdocProximityProfile.ISO_18013_5_2021,
                    p256,
                    setOf(MdocProtocolFeature.EXTENDED_REQUESTS),
                )
            }

            val x25519 = runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    KeyId("capability-x25519"),
                    KeySpec.Montgomery(MontgomeryCurve.X25519),
                    setOf(KeyUsage.KEY_AGREEMENT),
                )
            )
            val generic = MdocSessionCapabilities.forSession(
                MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
                x25519,
                emptySet(),
            )
            assertEquals(MdocSessionCurve.X25519, generic.selectedCurve)
            assertFailsWith<IllegalArgumentException> {
                MdocSessionCapabilities.forSession(
                    MdocProximityProfile.EUDI_ARF_3_FCAF_2026_08,
                    x25519,
                    emptySet(),
                )
            }
        }
    }
}
