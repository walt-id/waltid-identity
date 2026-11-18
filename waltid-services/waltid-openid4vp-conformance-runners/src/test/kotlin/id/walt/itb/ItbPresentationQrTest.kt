package id.walt.itb

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.*

class ItbPresentationQrTest {
    @Test
    fun qrRemainsTheInvocationWhenTheImageAlsoHasReadableEanOrUpcPatterns() {
        val invocation = "eu-eaap://?request_uri=https%3A%2F%2Fverifier.example%2Frequest"
        for ((format, digits) in listOf(BarcodeFormat.EAN_8 to "96385074", BarcodeFormat.UPC_E to "0123456")) {
            // Synthetic analogues of the two observed format-confusion risks; no live request is embedded.
            val qr = QRCodeWriter().encode(invocation, BarcodeFormat.QR_CODE, 350, 350)
            val barcode = MultiFormatWriter().encode(digits, format, 350, 80)
            val image = BufferedImage(350, 800, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply { color = Color.WHITE; fillRect(0, 0, 350, 800); dispose() }
            for (y in 0 until 350) for (x in 0 until 350) if (qr[x, y]) image.setRGB(x, y, 0)
            for (y in 0 until 80) for (x in 0 until 350) if (barcode[x, y]) image.setRGB(x, y + 360, 0)
            assertEquals(invocation, ItbPresentationQr.decode(ItbQrFixtures.png(image)), format.name)
        }
    }

    @Test
    fun preservesTheFullQrPayloadIncludingEscapedOpaqueParameters() {
        val invocation = "haip-vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest%3Fopaque%3Da%252Bb%252Fc%253D&request_uri_method=post"
        assertEquals(invocation, ItbPresentationQr.decode(ItbQrFixtures.qr(invocation)))
    }

    @Test
    fun rejectsARealNonQrBarcode() {
        val barcode = MultiFormatWriter().encode("96385074", BarcodeFormat.EAN_8, 350, 80)
        assertFailsWith<IllegalArgumentException> { ItbPresentationQr.decode(ItbQrFixtures.png(barcode)) }
    }

    @Test
    fun rejectsUnsupportedAndTruncatedImages() {
        for (content in listOf(
            byteArrayOf(1, 2, 3),
            ItbQrFixtures.qr("openid4vp://example").copyOf(20),
        )) assertFailsWith<IllegalArgumentException> { ItbPresentationQr.decode(content) }
    }

    @Test
    fun boundsEncodedBytesWithAnOtherwiseReadableQr() {
        val invocation = "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest"
        val qr = ItbQrFixtures.qr(invocation)
        // Trailing padding keeps the same PNG/QR readable while varying only the encoded size.
        assertEquals(invocation, ItbPresentationQr.decode(qr.copyOf(1_048_576)))
        assertFailsWith<IllegalArgumentException> { ItbPresentationQr.decode(qr.copyOf(1_048_577)) }
    }

    @Test
    fun boundsEachDimensionWithAnOtherwiseReadableQr() {
        val invocation = "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest"
        for ((atLimit, beyondLimit) in listOf(
            (2048 to 350) to (2049 to 350),
            (350 to 2048) to (350 to 2049),
        )) {
            assertEquals(invocation, ItbPresentationQr.decode(ItbQrFixtures.qr(invocation, atLimit.first, atLimit.second)))
            assertFailsWith<IllegalArgumentException> {
                ItbPresentationQr.decode(ItbQrFixtures.qr(invocation, beyondLimit.first, beyondLimit.second))
            }
        }
    }
}
