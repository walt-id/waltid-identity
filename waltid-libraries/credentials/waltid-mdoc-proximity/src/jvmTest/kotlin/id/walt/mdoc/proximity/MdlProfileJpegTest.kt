@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MdlProfileJpegTest {
    @Test
    fun responseImageByteStringsDecodeAsJpeg() = runTest {
        val fixture = MdlProfileResponseTest()
        val elements = fixture.responseElements(fixture.profile())
        for (identifier in listOf("portrait", "signature_usual_mark")) {
            val bytes = assertIs<CborByteString>(elements[identifier]).toByteArray()
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().single()
                try {
                    assertEquals("JPEG", reader.formatName.uppercase())
                    reader.input = input
                    val image = assertNotNull(reader.read(0))
                    assertEquals(2, image.width)
                    assertEquals(2, image.height)
                } finally {
                    reader.dispose()
                }
            }
        }
    }
}
