package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.ui.components.QrCodeRaster
import id.walt.walletdemo.compose.ui.components.encodeProximityQrCodeRaster
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import zxingcpp.Barcode
import zxingcpp.BarcodeFormat
import zxingcpp.BarcodeReader
import zxingcpp.Binarizer
import zxingcpp.ExperimentalWriterApi
import zxingcpp.ImageFormat
import zxingcpp.ImageView

class ProximityQrCodeEncoderIosTest {
    @Test
    fun roundTripsLongDeviceEngagementWithoutEci() {
        val payload = "mdoc:" + "A7v9kQ2_x-".repeat(120)

        val encoded = encodeProximityQrCodeRaster(payload)
        val decoded = roundTrip(encoded)

        assertEquals(payload, decoded.text)
        assertContentEquals(payload.encodeToByteArray(), decoded.bytes)
        assertFalse(decoded.hasECI)
        assertEquals("117x117:15a175a1b2151d12", matrixFingerprint(encoded))
    }

    @Test
    fun rejectsNonMdocNonAsciiAndOversizePayloads() {
        assertFails { encodeProximityQrCodeRaster("https://example.com") }
        assertFails { encodeProximityQrCodeRaster("mdoc:é") }
        assertFails { encodeProximityQrCodeRaster("mdoc:" + "A".repeat(4_000)) }
    }

    @OptIn(NativeRuntimeApi::class)
    @Test
    fun repeatedlyEncodesAcrossForcedGarbageCollections() {
        repeat(250) { index ->
            val encoded = encodeProximityQrCodeRaster("mdoc:$index-${"A7v9kQ2_x-".repeat(20)}")
            assertEquals(encoded.width * encoded.height, encoded.luminance.size)
            if (index % 10 == 0) GC.collect()
        }
        GC.collect()
    }

    @OptIn(ExperimentalWriterApi::class)
    private fun roundTrip(encoded: QrCodeRaster): Barcode = BarcodeReader().apply {
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

    private fun matrixFingerprint(raster: QrCodeRaster): String {
        var hash = 14695981039346656037uL
        raster.luminance.forEach { luminance ->
            hash = (hash xor if (luminance == 0.toByte()) 1uL else 0uL) * 1099511628211uL
        }
        return "${raster.width}x${raster.height}:${hash.toString(16).padStart(16, '0')}"
    }
}
