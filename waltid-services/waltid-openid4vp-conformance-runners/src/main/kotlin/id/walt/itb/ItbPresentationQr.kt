package id.walt.itb

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import id.walt.itb.ItbPortalInputError.Reason
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

/** Decode only the portal's QR payload; other barcode formats are not wallet invocations. */
internal object ItbPresentationQr {
    fun decode(content: ByteArray): String {
        if (content.size !in 1..1024 * 1024) throw ItbPortalInputError(Reason.INTERACTION_DOWNLOAD_SIZE)
        try {
            MemoryCacheImageInputStream(ByteArrayInputStream(content)).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) throw ItbPortalInputError(Reason.PRESENTATION_QR_IMAGE_FORMAT)
                val reader = readers.next()
                try {
                    reader.setInput(input, true, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width !in 1..2048 || height !in 1..2048) {
                        throw ItbPortalInputError(Reason.PRESENTATION_QR_IMAGE_DIMENSIONS)
                    }
                    val image = reader.read(0)
                    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
                    val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
                    val decoder = QRCodeReader()
                    return try {
                        decoder.decode(bitmap).text
                    } catch (_: NotFoundException) {
                        // Some clean ITB QR images defeat finder/alignment detection.
                        decoder.decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true)).text
                    }
                } finally {
                    reader.dispose()
                }
            }
        } catch (_: ReaderException) {
            throw ItbPortalInputError(Reason.PRESENTATION_QR_UNREADABLE)
        } catch (_: IOException) {
            throw ItbPortalInputError(Reason.PRESENTATION_QR_IMAGE_READ)
        }
    }
}
