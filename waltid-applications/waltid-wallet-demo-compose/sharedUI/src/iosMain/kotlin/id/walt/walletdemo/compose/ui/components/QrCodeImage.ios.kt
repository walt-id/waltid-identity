package id.walt.walletdemo.compose.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import id.walt.walletdemo.compose.logic.QrCodePayload
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import zxingcpp.Barcode
import zxingcpp.BarcodeFormat
import zxingcpp.CreatorOptions
import zxingcpp.ExperimentalWriterApi
import zxingcpp.WriterOptions
import zxingcpp.cinterop.ZXing_Image_data
import zxingcpp.cinterop.ZXing_Image_delete
import zxingcpp.cinterop.ZXing_Image_height
import zxingcpp.cinterop.ZXing_Image_width
import zxingcpp.cinterop.ZXing_LastErrorMsg
import zxingcpp.cinterop.ZXing_WriteBarcodeToImage
import zxingcpp.cinterop.ZXing_free

@OptIn(ExperimentalWriterApi::class)
internal actual fun encodeQrCode(payload: QrCodePayload): ImageBitmap {
    val qrCode = encodeQrCodeRaster(payload)
    val pixels = ByteArray(qrCode.luminance.size * RGBA_BYTES_PER_PIXEL)
    qrCode.luminance.forEachIndexed { index, value ->
        val offset = index * RGBA_BYTES_PER_PIXEL
        pixels[offset] = value
        pixels[offset + 1] = value
        pixels[offset + 2] = value
        pixels[offset + 3] = 0xFF.toByte()
    }

    return Image.makeRaster(
        imageInfo = ImageInfo(qrCode.width, qrCode.height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE),
        bytes = pixels,
        rowBytes = qrCode.width * RGBA_BYTES_PER_PIXEL,
    ).toComposeImageBitmap()
}

internal data class QrCodeRaster(
    val luminance: ByteArray,
    val width: Int,
    val height: Int,
)

@OptIn(ExperimentalForeignApi::class, ExperimentalWriterApi::class)
internal fun encodeQrCodeRaster(payload: QrCodePayload): QrCodeRaster {
    val creatorOptions = CreatorOptions(BarcodeFormat.QRCode).apply {
        options = when (payload) {
            is QrCodePayload.Text -> "ecLevel=M, eci=UTF-8"
            is QrCodePayload.Binary -> "ecLevel=M"
        }
    }
    val barcode = when (payload) {
        is QrCodePayload.Text -> Barcode(payload.value.encodeToByteArray(), creatorOptions)
        is QrCodePayload.Binary -> Barcode(payload.bytes, creatorOptions)
    }
    val writerOptions = WriterOptions().apply {
        scale = 1
        addQuietZones = false
    }
    val image = ZXing_WriteBarcodeToImage(barcode.cValue, writerOptions.cValue)
        ?: error("ZXing-C++ could not render the QR code${lastZxingError()?.let { ": $it" }.orEmpty()}")

    // ZXing-C++ 3.1.1's Kotlin Image.data accessor incorrectly frees this borrowed pixel buffer.
    // Keep ownership explicit here until that wrapper defect is fixed upstream.
    return try {
        val width = ZXing_Image_width(image)
        val height = ZXing_Image_height(image)
        val luminance = ZXing_Image_data(image)?.readBytes(width * height) ?: throw OutOfMemoryError()
        QrCodeRaster(luminance, width, height)
    } finally {
        ZXing_Image_delete(image)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun lastZxingError(): String? = ZXing_LastErrorMsg()?.let { error ->
    try {
        error.toKString()
    } finally {
        ZXing_free(error)
    }
}

private const val RGBA_BYTES_PER_PIXEL = 4
