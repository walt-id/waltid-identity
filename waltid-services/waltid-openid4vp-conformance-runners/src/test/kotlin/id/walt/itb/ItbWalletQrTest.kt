package id.walt.itb

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.Base64
import java.util.Random
import kotlin.test.*

class ItbWalletQrTest {
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
            assertEquals(invocation, ItbWalletQr.decode(ItbQrFixtures.png(image)), format.name)
        }
    }

    @Test
    fun preservesTheFullQrPayloadIncludingEscapedOpaqueParameters() {
        val invocation = "haip-vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest%3Fopaque%3Da%252Bb%252Fc%253D&request_uri_method=post"
        assertEquals(invocation, ItbWalletQr.decode(ItbQrFixtures.qr(invocation)))
    }

    @Test
    fun decodesACleanPortalStyleQrWhenFinderDetectionFails() {
        val invocation = "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest%2F" +
            "synthetic-10".repeat(11) + "&request_uri_method=get"
        // This generated 970px symbol reproduces the finder failure without a live verifier link.
        val pixels = QRCodeWriter().encode(invocation, BarcodeFormat.QR_CODE, 970, 970, mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.QR_VERSION to 18,
            EncodeHintType.QR_MASK_PATTERN to 0,
        ))
        assertEquals(invocation, ItbWalletQr.decode(ItbQrFixtures.png(pixels)))
    }

    @Test
    fun decodesACleanOfferQrWhenFinderDetectionReturnsAFormatError() {
        val opaque = ByteArray(185)
        Random(22).nextBytes(opaque)
        val reference = Base64.getUrlEncoder().withoutPadding().encodeToString(opaque)
        val offer = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer%2F$reference"
        // The regular detector samples the wrong grid for this valid, dense symbol.
        val pixels = QRCodeWriter().encode(offer, BarcodeFormat.QR_CODE, 1050, 1050, mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.QR_VERSION to 20,
            EncodeHintType.QR_MASK_PATTERN to 6,
        ))
        assertEquals(offer, ItbWalletQr.decode(ItbQrFixtures.png(pixels)))
    }

    @Test
    fun rejectsARealNonQrBarcode() {
        val barcode = MultiFormatWriter().encode("96385074", BarcodeFormat.EAN_8, 350, 80)
        assertFailsWith<IllegalArgumentException> { ItbWalletQr.decode(ItbQrFixtures.png(barcode)) }
    }

    @Test
    fun rejectsUnsupportedAndTruncatedImages() {
        for (content in listOf(
            byteArrayOf(1, 2, 3),
            ItbQrFixtures.qr("openid4vp://example").copyOf(20),
        )) assertFailsWith<IllegalArgumentException> { ItbWalletQr.decode(content) }
    }

    @Test
    fun boundsEncodedBytesWithAnOtherwiseReadableQr() {
        val invocation = "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest"
        val qr = ItbQrFixtures.qr(invocation)
        // Trailing padding keeps the same PNG/QR readable while varying only the encoded size.
        assertEquals(invocation, ItbWalletQr.decode(qr.copyOf(1_048_576)))
        assertFailsWith<IllegalArgumentException> { ItbWalletQr.decode(qr.copyOf(1_048_577)) }
    }

    @Test
    fun boundsEachDimensionWithAnOtherwiseReadableQr() {
        val invocation = "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest"
        for ((atLimit, beyondLimit) in listOf(
            (2048 to 350) to (2049 to 350),
            (350 to 2048) to (350 to 2049),
        )) {
            assertEquals(invocation, ItbWalletQr.decode(ItbQrFixtures.qr(invocation, atLimit.first, atLimit.second)))
            assertFailsWith<IllegalArgumentException> {
                ItbWalletQr.decode(ItbQrFixtures.qr(invocation, beyondLimit.first, beyondLimit.second))
            }
        }
    }
}
