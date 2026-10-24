package id.walt.mdoc.deviceengagement

import id.walt.mdoc.deviceengagement.retrieval.options.BleOptions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceEngagementTest {

    @Test
    fun testDeviceEngagementISOExample() {
        val hexEncodedDeviceEngagement =
            "a30063312e30018201d818584ba4010220012158205a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe225820b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc670281830201a300f401f50b5045efef742b2c4837a9a3b0e1d05a6917"
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
}
