@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.proximity

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RetrievalMethodSnapshotsTest {
    @Test fun engagementFreezesMethodsBeforeSuspendedKeyExport() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val key = runtime.generateMdocTestKey("engagement-snapshot", setOf(KeyUsage.KEY_AGREEMENT))
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val suspendedKey = object : Key by key {
            override val capabilities = key.capabilities.copy(publicKeyExporter = PublicKeyExporter {
                entered.complete(Unit)
                resume.await()
                requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey()
            })
        }
        val uuid = ByteArray(16).also { it[8] = 0x80.toByte() }
        val bands = byteArrayOf(0x04)
        val extensionBytes = byteArrayOf(7)
        val extensionTags = ulongArrayOf(24u)
        val items = mutableListOf<CborElement>(CborByteString(extensionBytes, *extensionTags))
        val extensions = linkedMapOf(9u to CborArray(items))
        val methods = mutableListOf<DeviceRetrievalMethod>(
            DeviceRetrievalMethod.Ble(centralMode = BleCentralMode(uuid)),
            DeviceRetrievalMethod.WifiAware(supportedBands = bands, extensions = extensions),
        )
        val profile = MdocProximityProfile.ISO_18013_5_ED2_DIS_2026
        try {
            val pending = async {
                MdocDeviceEngagementFactory().create(suspendedKey, methods,
                    EngagementContext(profile, 4096, MdocEngagementMode.Qr),
                    MdocSessionCapabilities.forSession(profile, key, emptySet()))
            }
            entered.await()
            uuid.fill(1); bands.fill(0); extensionBytes.fill(0); extensionTags.fill(99u)
            items.clear(); extensions.clear(); methods.clear()
            resume.complete(Unit)
            val retained = pending.await().engagement.value.deviceRetrievalMethods!!
            assertContentEquals(ByteArray(16).also { it[8] = 0x80.toByte() },
                assertIs<DeviceRetrievalMethod.Ble>(retained[0]).centralMode!!.uuid)
            val wifi = assertIs<DeviceRetrievalMethod.WifiAware>(retained[1])
            assertContentEquals(byteArrayOf(0x04), wifi.supportedBands)
            val extension = assertIs<CborByteString>(assertIs<CborArray>(wifi.extensions[9u]).single())
            assertContentEquals(byteArrayOf(7), extension.toByteArray())
            assertEquals(listOf(24uL), extension.tags)
        } finally {
            resume.complete(Unit)
            key.capabilities.deleter?.delete()
            runtime.close()
        }
    }

    @Test fun preparedMethodFactsOwnProviderValuesAndTheirProjections() = runTest {
        val bands = byteArrayOf(0x04)
        val method = DeviceRetrievalMethod.WifiAware(supportedBands = bands)
        val loopback = FakeProximityLoopback.create(kind = ProximityTransportKind.WIFI_AWARE)
        val transport = FakePreparedTransport(method, loopback.holder)
        val prepared = PreparedTransports(listOf(transport), emptyMap())
        bands.fill(0)
        assertIs<DeviceRetrievalMethod.WifiAware>(prepared.connectionMethods.single()).supportedBands.fill(0)
        assertContentEquals(byteArrayOf(0x04),
            assertIs<DeviceRetrievalMethod.WifiAware>(prepared.connectionMethods.single()).supportedBands)
        transport.close(ProximityCloseReason.COMPLETED)
    }

}
