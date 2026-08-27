@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlin.ExperimentalUnsignedTypes::class,
)

package id.walt.mdoc.proximity

import id.walt.cose.coseCompliantCbor
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
import kotlinx.serialization.cbor.CborBoolean
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.decodeFromByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MdocCapabilitiesTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `first-edition profile emits only the common version 1 0 baseline`() = runTest {
        withContext(Dispatchers.Default) {
            assertEquals("iso-18013-5:2021", MdocProximityProfile.ISO_18013_5_2021.id)
            val p256 = runtime.generateMdocTestKey("capability-2021", setOf(KeyUsage.KEY_AGREEMENT))
            val capabilities = MdocSessionCapabilities.forSession(
                MdocProximityProfile.ISO_18013_5_2021,
                p256,
                emptySet(),
            )
            val engagement = MdocDeviceEngagementFactory().create(
                eDeviceKey = p256,
                methods = listOf(DeviceRetrievalMethod.Nfc(1_024u, 1_024u)),
                context = EngagementContext(capabilities.profile, 1_048_576, MdocEngagementMode.Qr),
                capabilities = capabilities,
            ).engagement

            assertEquals(MdocSessionCurve.P256, capabilities.selectedCurve)
            assertEquals(DeviceEngagement.VERSION_1_0, engagement.value.version)
            assertEquals(null, engagement.value.originInfos)
            assertEquals(null, engagement.value.capabilities)
            assertEdition2FieldsAbsent(engagement.encodedCopy())
            assertFailsWith<IllegalArgumentException> {
                MdocSessionCapabilities.forSession(
                    MdocProximityProfile.ISO_18013_5_2021,
                    p256,
                    setOf(MdocProtocolFeature.EXTENDED_REQUESTS),
                )
            }
        }
    }

    @Test
    fun `edition 2 profile without selected extensions also emits version 1 0`() = runTest {
        withContext(Dispatchers.Default) {
            val p256 = runtime.generateMdocTestKey(
                "capability-ed2-baseline",
                setOf(KeyUsage.KEY_AGREEMENT),
            )
            val capabilities = MdocSessionCapabilities.forSession(
                MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
                p256,
                emptySet(),
            )
            val engagement = MdocDeviceEngagementFactory().create(
                eDeviceKey = p256,
                methods = listOf(DeviceRetrievalMethod.Nfc(1_024u, 1_024u)),
                context = EngagementContext(capabilities.profile, 1_048_576, MdocEngagementMode.Qr),
                capabilities = capabilities,
            ).engagement

            assertEquals(DeviceEngagement.VERSION_1_0, engagement.value.version)
            assertEdition2FieldsAbsent(engagement.encodedCopy())
        }
    }

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

            val engagement = MdocDeviceEngagementFactory().create(
                eDeviceKey = p256,
                methods = listOf(DeviceRetrievalMethod.Nfc(1_024u, 1_024u)),
                context = EngagementContext(capabilities.profile, 1_048_576, MdocEngagementMode.Qr),
                capabilities = capabilities,
            ).engagement
            assertEquals(DeviceEngagement.VERSION_1_1, engagement.value.version)
            assertEquals(emptyList(), engagement.value.originInfos)
            assertEquals(true, engagement.value.capabilities?.readerAuthAll)
            assertEquals(false, engagement.value.capabilities?.extendedRequests)
            val encoded = coseCompliantCbor.decodeFromByteArray<CborElement>(
                engagement.encodedCopy()
            ) as CborMap
            assertTrue(CborInteger(5) in encoded)
            assertTrue(CborInteger(6) in encoded)
            val encodedCapabilities = encoded[CborInteger(6)] as CborMap
            assertEquals(CborBoolean(false), encodedCapabilities[CborInteger(4)])
        }
    }

    @Test
    fun `session selection rejects capabilities forbidden by the active profile`() = runTest {
        withContext(Dispatchers.Default) {
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

    private fun assertEdition2FieldsAbsent(encodedEngagement: ByteArray) {
        val encoded = coseCompliantCbor.decodeFromByteArray<CborElement>(encodedEngagement) as CborMap
        assertFalse(CborInteger(5) in encoded)
        assertFalse(CborInteger(6) in encoded)
    }
}
