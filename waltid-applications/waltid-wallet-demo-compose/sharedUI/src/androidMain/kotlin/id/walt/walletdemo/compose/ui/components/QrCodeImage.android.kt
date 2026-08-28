package id.walt.walletdemo.compose.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal actual fun encodeProximityQrCode(payload: String): ImageBitmap {
    validateProximityQrCodePayload(payload)
    val hints = mapOf<EncodeHintType, Any>(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 0,
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) DarkPixel else LightPixel
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

private const val DarkPixel = 0xFF000000.toInt()
private const val LightPixel = 0xFFFFFFFF.toInt()
