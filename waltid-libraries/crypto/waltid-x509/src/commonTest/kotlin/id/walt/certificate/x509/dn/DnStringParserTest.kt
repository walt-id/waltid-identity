package id.walt.certificate.x509.dn

import kotlin.test.Test
import kotlin.test.assertEquals

class DnStringParserTest {

    @Test
    fun shouldParseSimpleDnString() {
        val result = DnStringParser("  dN=Walt.id  ").parse()
        assertEquals(1, result.rdnList.size)
        assertEquals(1, result.rdnList.first().size)

        val dn = result.rdnList[0][0]
        assertEquals("dn", dn.type.name)
        assertEquals("Walt.id", dn.value)

        assertEquals("DN=Walt.id", result.toString())
    }

    @Test
    fun shouldParseSimpleDnWithEscapedCommaString() {
        val result = DnStringParser("  cn=Walt.id\\, and more  ").parse()
        assertEquals(1, result.rdnList.size)
        assertEquals(1, result.rdnList.first().size)

        val dn = result.rdnList[0][0]
        assertEquals("cn", dn.type.name)
        assertEquals("Walt.id, and more", dn.value)

        assertEquals("CN=Walt.id\\, and more", result.toString())
    }

    @Test
    fun shouldParseDnWithSecondAttribute() {
        val result = DnStringParser("  cn=Walt.id\\+ and more+serialNumber=23938475  ").parse()
        assertEquals(1, result.rdnList.size)
        assertEquals(2, result.rdnList.first().size)

        val cn = result.rdnList[0][0]
        assertEquals("cn", cn.type.name)
        assertEquals("Walt.id+ and more", cn.value)

        val serial = result.rdnList[0][1]
        assertEquals("serialNumber", serial.type.name)
        assertEquals("23938475", serial.value)
        assertEquals("CN=Walt.id\\+ and more+SERIALNUMBER=23938475", result.toString())
    }

    @Test
    fun shouldParseDnWithLeadingAndTrailingSpace() {
        val result = DnStringParser("  cn= \\  Walt.id\\+ and more \\ ").parse()
        assertEquals(1, result.rdnList.size)
        assertEquals(1, result.rdnList.first().size)

        val cn = result.rdnList[0][0]
        assertEquals("cn", cn.type.name)
        assertEquals("  Walt.id+ and more  ", cn.value)
    }
}