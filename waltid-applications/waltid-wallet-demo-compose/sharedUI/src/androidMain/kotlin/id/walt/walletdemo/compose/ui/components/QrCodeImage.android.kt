package id.walt.walletdemo.compose.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import id.walt.walletdemo.compose.logic.QrCodePayload
import java.nio.charset.StandardCharsets

internal actual fun encodeQrCode(payload: QrCodePayload): ImageBitmap {
    val hints = mutableMapOf<EncodeHintType, Any>(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
    )
    val content = when (payload) {
        is QrCodePayload.Text -> payload.value.also {
            hints[EncodeHintType.CHARACTER_SET] = StandardCharsets.UTF_8.name()
        }
        is QrCodePayload.Binary -> String(payload.bytes, StandardCharsets.ISO_8859_1)
    }
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) DarkPixel else LightPixel
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

private const val DarkPixel = 0xFF000000.toInt()
private const val LightPixel = 0xFFFFFFFF.toInt()
