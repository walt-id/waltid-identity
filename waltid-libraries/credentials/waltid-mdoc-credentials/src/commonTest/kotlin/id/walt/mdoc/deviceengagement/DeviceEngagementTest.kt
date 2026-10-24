package id.walt.mdoc.deviceengagement

import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.StringElement
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.deviceengagement.retrieval.methods.device.BleDeviceRetrieval
import id.walt.mdoc.deviceengagement.retrieval.methods.device.NfcDeviceRetrieval
import id.walt.mdoc.deviceengagement.retrieval.options.BleOptions
import id.walt.mdoc.deviceengagement.retrieval.options.NfcOptions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceEngagementTest {

    private val hexEncodedDeviceEngagement =
        "a30063312e30018201d818584ba4010220012158205a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe225820b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc670281830201a300f401f50b5045efef742b2c4837a9a3b0e1d05a6917"

    @Test
    fun testDeviceEngagementISOExample() {
        val engagement = DeviceEngagement.fromCBORHex(hexEncodedDeviceEngagement)
        assertEquals("1.0", engagement.version)
        assertEquals(1, engagement.security.cipherSuite)
        assertContentEquals(
            ("a4010220012158205a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe" +
                    "225820b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc67").hexToByteArray(),
            engagement.security.eDeviceKeyBytes.value,
        )
        val method = assertNotNull(engagement.deviceRetrievalMethods).single()
        assertEquals(2u, method.type.value)
        assertEquals(1u, method.version)
        val options = assertIs<BleOptions>(method.retrievalOptions)
        assertFalse(options.supportMDocPeripheralServerMode)
        assertTrue(options.supportMDocCentralClientMode)
        assertContentEquals("45efef742b2c4837a9a3b0e1d05a6917".hexToByteArray(), options.mdocCentralClientModeUUID)
        assertNull(options.mdocPeripheralServerModeUUID)
        assertNull(options.mdocPeripheralServerModeDeviceAddress)
        assertNull(options.mdocPeripheralServerModeL2CAPPSM)
        assertNull(engagement.serverRetrievalMethods)
        assertNull(engagement.protocolInfo)
        assertNull(engagement.originInfos)
        assertNull(engagement.capabilities)
        assertTrue(engagement.optional.isEmpty())
    }

    @Test
    fun originInfosSelectVersion11() {
        val engagement = DeviceEngagement.fromCBORHex(hexEncodedDeviceEngagement)
        val capabilities = Capabilities(readerAuthAllSupport = true)
        assertFailsWith<IllegalArgumentException> { engagement.copy(capabilities = capabilities) }

        val origin = OriginInfo(5u, 7u, mapOf("asdf" to 5.toDataElement()))
        for (origins in listOf(emptyList(), listOf(origin))) {
            for (optionalCapabilities in listOf(null, capabilities)) {
                val extended = engagement.copy(originInfos = origins, capabilities = optionalCapabilities)
                assertEquals("1.1", extended.version)
                assertEquals("1.1", assertIs<StringElement>(extended.toMapElement().value[MapKey(0)]).value)
            }
        }
    }

    @Test
    fun distinctRetrievalMethodPairsAreAccepted() {
        val engagement = DeviceEngagement.fromCBORHex(hexEncodedDeviceEngagement)
        val ble = assertIs<BleDeviceRetrieval>(engagement.deviceRetrievalMethods!!.single())
        val nfc = NfcDeviceRetrieval(
            retrievalOptions = NfcOptions(commandDataFieldMaxLength = 5u, responseDataFieldMaxLength = 7u),
        )
        for ((methods, expected) in listOf(
            listOf(ble, nfc) to listOf(2u to 1u, 1u to 1u),
            listOf(ble, ble.copy(version = 2u)) to listOf(2u to 1u, 2u to 2u),
        )) {
            val extended = engagement.copy(deviceRetrievalMethods = methods)
            assertEquals(expected, extended.deviceRetrievalMethods!!.map { it.type.value to it.version })
        }
    }

    @Test
    fun duplicateRetrievalMethodPairsAreRejected() {
        val engagement = DeviceEngagement.fromCBORHex(hexEncodedDeviceEngagement)
        val ble = assertIs<BleDeviceRetrieval>(engagement.deviceRetrievalMethods!!.single())
        val otherOptions = ble.copy(retrievalOptions = ble.retrievalOptions.copy(
            mdocCentralClientModeUUID = "00112233445566778899aabbccddeeff".hexToByteArray(),
        ))
        for (duplicate in listOf(ble, otherOptions)) {
            assertFailsWith<IllegalArgumentException> {
                engagement.copy(deviceRetrievalMethods = listOf(ble, duplicate))
            }
        }
    }
}
