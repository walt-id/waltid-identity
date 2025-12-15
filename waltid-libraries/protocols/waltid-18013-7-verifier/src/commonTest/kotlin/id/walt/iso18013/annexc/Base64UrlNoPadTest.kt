package id.walt.iso18013.annexc

import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
import kotlin.test.Test
import kotlin.test.assertContentEquals

class Base64UrlNoPadTest {

    @Test
    fun `encode-decode roundtrip`() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 127, -128, -1)
        val encoded = Base64UrlNoPad.encode(bytes)
        val decoded = Base64UrlNoPad.decode(encoded)
        assertContentEquals(bytes, decoded)
    }
}

