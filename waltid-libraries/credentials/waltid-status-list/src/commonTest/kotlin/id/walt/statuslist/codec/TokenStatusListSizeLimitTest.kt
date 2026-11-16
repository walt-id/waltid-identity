package id.walt.statuslist.codec

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import id.walt.statuslist.compression.ZlibCompressor

class TokenStatusListSizeLimitTest {
    @Test fun highlyCompressedInputCannotExceedTheConsumerLimit() {
        val bytes = ByteArray(64 * 1024)
        val encoded = TokenStatusListCodec.encode(bytes)
        assertFailsWith<IllegalArgumentException> { TokenStatusListCodec.decode(encoded, bytes.size - 1) }
        assertContentEquals(bytes, TokenStatusListCodec.decode(encoded, bytes.size))
        assertFailsWith<IllegalArgumentException> { TokenStatusListCodec.decode(encoded, -1) }
        assertFailsWith<IllegalArgumentException> { ZlibCompressor.decompress(byteArrayOf(0x78, 0x20), 128) }
        assertContentEquals(ByteArray(0), TokenStatusListCodec.decode(TokenStatusListCodec.encode(ByteArray(0)), 0))
    }
}
