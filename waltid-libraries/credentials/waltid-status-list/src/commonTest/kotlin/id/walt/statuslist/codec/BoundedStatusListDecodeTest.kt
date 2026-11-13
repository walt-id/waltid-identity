package id.walt.statuslist.codec

import id.walt.statuslist.compression.StatusListSizeLimitException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BoundedStatusListDecodeTest {
    @Test
    fun legacyPublishedBase64AndUrlAlphabetKeepTheSameBoundedBytes() {
        // StatusList2021 2023-04-27 section 2.2 example, expressed in both RFC 4648 alphabets.
        val url = "H4sIAAAAAAAAA-3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
        val standard = "H4sIAAAAAAAAA+3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
        for (encoded in listOf(standard, url)) {
            assertContentEquals(ByteArray(16_384), StatusList2021Codec.decodeBounded(encoded, 16_384))
            assertFailsWith<StatusListSizeLimitException> { StatusList2021Codec.decodeBounded(encoded, 16_383) }
        }
    }
}
