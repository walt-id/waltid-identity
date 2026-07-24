package id.walt.certificate.x509.dn

import id.walt.certificate.x509.dn.RelativeDistinguishedNameEscapingUtil.escapeString
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeDistinguishedNameEscapingUtilTest {

    @Test
    fun shouldEscapeFirstAndLastSpace() {
        assertEquals("\\  abcd \\ ", escapeString("  abcd  "))
    }

    @Test
    fun shouldEscapeFirstCharHash() {
        assertEquals("\\# hello ##", escapeString("# hello ##"))
    }

    @Test
    fun shouldEscapeSpecialCharacters() {
        assertEquals("hello\\,walt\\;again\\<and\\>how\\\\are\\\"you", escapeString("hello,walt;again<and>how\\are\"you"))
    }
}