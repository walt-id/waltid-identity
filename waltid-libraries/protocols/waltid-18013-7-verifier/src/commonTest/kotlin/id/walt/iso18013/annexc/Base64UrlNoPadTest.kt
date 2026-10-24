package id.walt.iso18013.annexc

import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Base64UrlNoPadTest {

    @Test
    fun `base64url matches unpadded wire values`() {
        val cases = listOf(
            byteArrayOf(0, 1, 2, 3, 4, 5, 127, -128, -1) to "AAECAwQFf4D_",
            byteArrayOf(-1) to "_w",
            byteArrayOf(-5, -1) to "-_8",
            byteArrayOf() to "",
        )
        for ((bytes, wire) in cases) {
            assertEquals(wire, bytes.encodeToBase64Url())
            assertContentEquals(bytes, wire.base64UrlDecode())
        }
    }
}

