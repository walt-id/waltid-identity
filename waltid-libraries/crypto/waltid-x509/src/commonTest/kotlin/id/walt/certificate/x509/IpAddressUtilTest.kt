package id.walt.certificate.x509

import kotlin.test.Test
import kotlin.test.assertEquals

class IpAddressUtilTest {

    @Test
    fun shouldParseBroadCastAddress() {
        assertEquals("ffffffff", IpAddressUtil.parseString("255.255.255.255").toHexString())
    }

    @Test
    fun shouldParsePrivateAddress() {
        assertEquals("c0a80001", IpAddressUtil.parseString("192.168.0.1").toHexString())
    }

    @Test
    fun shouldReadIpv4ByteArray() {
        assertEquals("127.0.0.1", IpAddressUtil.byteArrayToIpAddress(byteArrayOf(127, 0, 0, 1)))
        assertEquals("255.255.127.0", IpAddressUtil.byteArrayToIpAddress(byteArrayOf(-1, -1, 127, 0)))
    }
}