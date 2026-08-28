package id.walt.walletdemo.compose.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.DecoderResult
import com.google.zxing.qrcode.decoder.Decoder
import id.walt.walletdemo.compose.ui.components.encodeProximityQrCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProximityQrCodeEncoderAndroidTest {
    @Test
    fun roundTripsLongDeviceEngagementWithoutEci() {
        val payload = "mdoc:" + "A7v9kQ2_x-".repeat(120)

        val decoded = decode(encodeProximityQrCode(payload))

        assertEquals(payload, decoded.text)
        assertEquals(1, decoded.symbologyModifier)
    }

    @Test
    fun rejectsNonMdocNonAsciiAndOversizePayloads() {
        assertFails { encodeProximityQrCode("https://example.com") }
        assertFails { encodeProximityQrCode("mdoc:é") }
        assertFails { encodeProximityQrCode("mdoc:" + "A".repeat(4_000)) }
    }

    private fun decode(image: ImageBitmap): DecoderResult {
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val matrix = BitMatrix(image.width, image.height)
        pixels.forEachIndexed { index, pixel ->
            if (pixel and 0x00FFFFFF == 0) {
                matrix.set(index % image.width, index / image.width)
            }
        }
        return Decoder().decode(matrix)
    }
}
