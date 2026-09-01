package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.QrCodePayload
import id.walt.walletdemo.compose.ui.components.encodeQrCode
import id.walt.walletdemo.compose.ui.components.encodeQrCodeRaster
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import zxingcpp.Barcode
import zxingcpp.BarcodeFormat
import zxingcpp.BarcodeReader
import zxingcpp.Binarizer
import zxingcpp.ExperimentalWriterApi
import zxingcpp.ImageFormat
import zxingcpp.ImageView

class QrCodeEncoderIosTest {
    @Test
    fun encodesConfirmedPlaceholderPayload() {
        val payload = "12/POC(N)000001|Aung Min Thu|1990-06-15|Male|Myanmar|Yangon, Myanmar|true"
        val image = encodeQrCode(QrCodePayload.Text(payload))

        assertTrue(image.width > 0)
        assertEquals(image.width, image.height)
    }

    @Test
    fun roundTripsUnicodeTextWithUtf8Eci() {
        val payload = "အောင် မင်းသူ|ရန်ကုန်၊ မြန်မာ"

        val decoded = roundTrip(QrCodePayload.Text(payload))

        assertEquals(payload, decoded.text)
        assertContentEquals(payload.encodeToByteArray(), decoded.bytes)
        assertTrue(decoded.hasECI)
    }

    @Test
    fun roundTripsBinaryPayloadWithoutTextConversion() {
        val payload = byteArrayOf(0xDC.toByte(), 0x03, 0x00, 0xFF.toByte(), 0x41)

        val decoded = roundTrip(QrCodePayload.Binary(payload))

        assertContentEquals(payload, decoded.bytes)
        assertTrue(decoded.hasECI)
    }

    @Test
    fun textQrEncoderMatchesCrossPlatformModuleFingerprint() {
        assertEquals(
            "37x37:77c32a6ddc54699d",
            matrixFingerprint(encodeQrCodeRaster(QrCodePayload.Text("အောင် မင်းသူ|ရန်ကုန်၊ မြန်မာ"))),
        )
    }

    @Test
    fun binaryQrEncoderMatchesNativeIosModuleFingerprint() {
        // ZXing-C++ marks arbitrary bytes with ECI 899; native Swift uses the same engine and options.
        assertEquals(
            "21x21:0688cb671d6022d3",
            matrixFingerprint(
                encodeQrCodeRaster(
                    QrCodePayload.Binary(byteArrayOf(0xDC.toByte(), 0x03, 0x00, 0xFF.toByte(), 0x41)),
                ),
            ),
        )
    }

    @OptIn(NativeRuntimeApi::class)
    @Test
    fun repeatedlyEncodesAcrossForcedGarbageCollections() {
        repeat(250) { index ->
            val payload = if (index % 2 == 0) {
                QrCodePayload.Text("အောင် မင်းသူ|$index")
            } else {
                QrCodePayload.Binary(byteArrayOf(0xDC.toByte(), 0x03, index.toByte(), 0xFF.toByte()))
            }
            val encoded = encodeQrCodeRaster(payload)
            assertEquals(encoded.width * encoded.height, encoded.luminance.size)
            if (index % 10 == 0) GC.collect()
        }
        GC.collect()
    }

    @OptIn(ExperimentalWriterApi::class)
    private fun roundTrip(payload: QrCodePayload): Barcode {
        val encoded = encodeQrCodeRaster(payload)
        return BarcodeReader().apply {
            formats = setOf(BarcodeFormat.QRCode)
            isPure = true
            binarizer = Binarizer.BoolCast
        }.read(
            ImageView(
                data = encoded.luminance,
                width = encoded.width,
                height = encoded.height,
                format = ImageFormat.Lum,
            )
        ).single()
    }

    private fun matrixFingerprint(raster: id.walt.walletdemo.compose.ui.components.QrCodeRaster): String {
        var hash = 14695981039346656037uL
        raster.luminance.forEach { luminance ->
            hash = (hash xor if (luminance == 0.toByte()) 1uL else 0uL) * 1099511628211uL
        }
        return "${raster.width}x${raster.height}:${hash.toString(16).padStart(16, '0')}"
    }
}
