package id.walt.walletdemo.compose.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterImageSupportTest {

    @Test
    fun acceptsHttpsRasterUrlsAndUrlsWithoutExtension() {
        assertTrue(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/logo.png"))
        assertTrue(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/logo.PNG?v=1"))
        assertTrue(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/logo"))
        assertTrue(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/v1/images/abc123"))
    }

    @Test
    fun acceptsHttpsSvgAndAvifUrls() {
        assertTrue(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/logo.svg"))
        assertTrue(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/logo.svgz#hash"))
        assertTrue(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/logo.avif"))
    }

    @Test
    fun rejectsNonHttpsAndMarkupOrDocumentUrls() {
        assertFalse(RasterImageSupport.isHttpsDisplayImageUrl(null))
        assertFalse(RasterImageSupport.isHttpsDisplayImageUrl("http://issuer.example/logo.png"))
        assertFalse(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/logo.html"))
        assertFalse(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/logo.xml"))
        assertFalse(RasterImageSupport.isHttpsDisplayImageUrl("https://issuer.example/logo.pdf"))
    }

    @Test
    fun detectsRasterMagicBytes() {
        assertTrue(RasterImageSupport.looksLikeRaster(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D)))
        assertTrue(RasterImageSupport.looksLikeRaster(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertTrue(RasterImageSupport.looksLikeRaster("GIF89a....".encodeToByteArray()))
        assertTrue(
            RasterImageSupport.looksLikeRaster(
                ("RIFF" + "...." + "WEBP").encodeToByteArray(),
            ),
        )
        assertFalse(RasterImageSupport.looksLikeRaster(ByteArray(0)))
        assertFalse(RasterImageSupport.looksLikeRaster("<svg xmlns".encodeToByteArray()))
    }

    @Test
    fun detectsSvgPayloadsSeparatelyFromHtml() {
        assertTrue(RasterImageSupport.looksLikeSvg("<svg xmlns".encodeToByteArray()))
        assertTrue(RasterImageSupport.looksLikeSvg("  <?xml version=\"1.0\"><svg".encodeToByteArray()))
        assertFalse(RasterImageSupport.looksLikeSvg("<html><body>not an image".encodeToByteArray()))
        assertFalse(RasterImageSupport.looksLikeSvg(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertFalse(RasterImageSupport.looksLikeUnsupportedMarkup("<svg xmlns".encodeToByteArray()))
        assertFalse(RasterImageSupport.looksLikeUnsupportedMarkup("  <?xml version=\"1.0\"><svg".encodeToByteArray()))
        assertTrue(RasterImageSupport.looksLikeUnsupportedMarkup("<!DOCTYPE html>".encodeToByteArray()))
        assertTrue(RasterImageSupport.looksLikeUnsupportedMarkup("<html><body>not an image".encodeToByteArray()))
        assertTrue(RasterImageSupport.looksLikeUnsupportedMarkup("<?xml version=\"1.0\"><rss".encodeToByteArray()))
        assertFalse(RasterImageSupport.looksLikeUnsupportedMarkup(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    }
}
