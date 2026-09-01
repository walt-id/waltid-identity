package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.DecoderResult
import com.google.zxing.qrcode.decoder.Decoder
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.DisplayValue
import id.walt.walletdemo.compose.logic.QrCodePayload
import id.walt.walletdemo.compose.ui.components.ClaimValueRow
import id.walt.walletdemo.compose.ui.components.encodeQrCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClaimValueRowAndroidTest {
    @Test
    fun qrEncoderSupportsConfirmedPlaceholderPayload() {
        val payload = "12/POC(N)000001|Aung Min Thu|1990-06-15|Male|Myanmar|Yangon, Myanmar|true"
        val encoded = encodeQrCode(QrCodePayload.Text(payload))

        assertEquals(37, encoded.width)
        assertEquals(payload, decode(encoded).text)
    }

    @Test
    fun qrEncoderPreservesCompactVdsBytes() {
        val payload = byteArrayOf(0xDC.toByte(), 0x03, 0x00, 0xFF.toByte(), 0x7F)

        val byteSegments = decode(encodeQrCode(QrCodePayload.Binary(payload))).byteSegments

        assertEquals(1, byteSegments?.size)
        assertContentEquals(payload, byteSegments?.single())
    }

    @Test
    fun largeListRendersABoundedPreview() = runComposeUiTest {
        setContent {
            WalletDemoTheme {
                ClaimValueRow(
                    item = ClaimItem(
                        path = ClaimItemPath.topLevel("unknown_binary"),
                        label = "Unknown binary",
                        value = DisplayValue.ListValue(
                            values = List(30) { index -> DisplayValue.NumberValue("item $index") },
                        ),
                    ),
                )
            }
        }

        onNodeWithText("item 24").assertExists()
        onNodeWithText("item 25").assertDoesNotExist()
        onNodeWithText("Showing first 25 of 30 items").assertExists()
    }

    private fun decode(image: androidx.compose.ui.graphics.ImageBitmap): DecoderResult {
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
