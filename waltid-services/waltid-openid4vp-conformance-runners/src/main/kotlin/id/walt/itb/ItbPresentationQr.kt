package id.walt.itb

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

/** Decode only the portal's QR payload; other barcode formats are not wallet invocations. */
internal object ItbPresentationQr {
    fun decode(content: ByteArray): String {
        require(content.size in 1..1024 * 1024) { "The ITB presentation QR exceeds the size limit" }
        try {
            MemoryCacheImageInputStream(ByteArrayInputStream(content)).use { input ->
                val readers = ImageIO.getImageReaders(input)
                require(readers.hasNext()) { "The ITB presentation QR is not a supported image" }
                val reader = readers.next()
                try {
                    reader.setInput(input, true, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    require(width in 1..2048 && height in 1..2048) { "The ITB presentation QR exceeds the image limits" }
                    val image = reader.read(0)
                    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
                    val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
                    return QRCodeReader().decode(bitmap).text
                } finally {
                    reader.dispose()
                }
            }
        } catch (_: ReaderException) {
            throw IllegalArgumentException("The ITB presentation image does not contain a readable QR code")
        } catch (_: IOException) {
            throw IllegalArgumentException("The ITB presentation QR image could not be read")
        }
    }
}
