package id.walt.walletdemo.compose.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import id.walt.walletdemo.compose.logic.QrCodePayload
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.filterWithName
import platform.CoreImage.kCIFormatRGBA8
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.setValue

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal actual fun encodeQrCode(payload: QrCodePayload): ImageBitmap {
    val message = when (payload) {
        is QrCodePayload.Text -> payload.value.encodeToByteArray()
        is QrCodePayload.Binary -> payload.bytes
    }
    require(message.isNotEmpty()) { "QR code payload must not be empty" }
    val filter = checkNotNull(CIFilter.filterWithName("CIQRCodeGenerator"))
    filter.setValue(message.toNSData(), forKey = "inputMessage")
    filter.setValue("M", forKey = "inputCorrectionLevel")
    val output = checkNotNull(filter.outputImage)
    val width = CGRectGetWidth(output.extent).toInt()
    val height = CGRectGetHeight(output.extent).toInt()
    val rowBytes = width * RGBA_BYTES_PER_PIXEL
    val pixels = ByteArray(rowBytes * height)

    pixels.usePinned { pinned ->
        qrContext.render(
            image = output,
            toBitmap = pinned.addressOf(0),
            rowBytes = rowBytes.toLong(),
            bounds = output.extent,
            format = kCIFormatRGBA8,
            colorSpace = qrColorSpace,
        )
    }

    return Image.makeRaster(
        imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE),
        bytes = pixels,
        rowBytes = rowBytes,
    ).toComposeImageBitmap()
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

private val qrContext = CIContext.contextWithOptions(null)
@OptIn(ExperimentalForeignApi::class)
private val qrColorSpace = checkNotNull(CGColorSpaceCreateDeviceRGB())
private const val RGBA_BYTES_PER_PIXEL = 4
