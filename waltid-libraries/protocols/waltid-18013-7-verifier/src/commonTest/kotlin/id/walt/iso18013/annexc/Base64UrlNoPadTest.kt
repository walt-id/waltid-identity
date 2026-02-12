package id.walt.iso18013.annexc

import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlin.test.Test
import kotlin.test.assertContentEquals

class Base64UrlNoPadTest {

    @Test
    fun `encode-decode roundtrip`() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 127, -128, -1)
        val encoded = bytes.encodeToBase64Url()
        val decoded = encoded.base64UrlDecode()
        assertContentEquals(bytes, decoded)
    }
}

