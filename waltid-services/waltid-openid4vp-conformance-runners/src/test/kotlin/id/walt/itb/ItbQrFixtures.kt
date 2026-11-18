package id.walt.itb

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal object ItbQrFixtures {
    fun qr(value: String, width: Int = 350, height: Int = 350): ByteArray =
        png(QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, width, height))

    fun png(bits: BitMatrix): ByteArray = png(BufferedImage(bits.width, bits.height, BufferedImage.TYPE_INT_RGB).apply {
        for (y in 0 until height) for (x in 0 until width) setRGB(x, y, if (bits[x, y]) 0 else 0xffffff)
    })

    fun png(image: BufferedImage): ByteArray = ByteArrayOutputStream().use { out ->
        check(ImageIO.write(image, "png", out))
        out.toByteArray()
    }
}
