package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.roundToInt

internal expect fun encodeProximityQrCode(payload: String): ImageBitmap

internal fun validateProximityQrCodePayload(payload: String) {
    require(payload.startsWith(PROXIMITY_QR_PREFIX)) {
        "Device engagement QR payload must use the mdoc scheme"
    }
    require(payload.all { it.code in ASCII_RANGE }) {
        "Device engagement QR payload must contain only ASCII text"
    }
    require(payload.encodeToByteArray().size <= MAXIMUM_PROXIMITY_PAYLOAD_BYTES) {
        "Device engagement QR payload exceeds the $MAXIMUM_PROXIMITY_PAYLOAD_BYTES-byte limit"
    }
}

@Composable
internal fun QrCodeCanvas(qrCode: ImageBitmap, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color.White)) {
        val totalModules = qrCode.width + QR_QUIET_ZONE_MODULES * 2
        val moduleSize = floor(minOf(size.width, size.height) / totalModules)
            .roundToInt()
            .coerceAtLeast(1)
        val renderedSize = totalModules * moduleSize
        val qrSize = qrCode.width * moduleSize
        val origin = IntOffset(
            x = ((size.width - renderedSize) / 2f).roundToInt() + QR_QUIET_ZONE_MODULES * moduleSize,
            y = ((size.height - renderedSize) / 2f).roundToInt() + QR_QUIET_ZONE_MODULES * moduleSize,
        )
        drawImage(
            image = qrCode,
            dstOffset = origin,
            dstSize = IntSize(qrSize, qrSize),
            filterQuality = FilterQuality.None,
        )
    }
}

private val ASCII_RANGE = 0..0x7F
private const val PROXIMITY_QR_PREFIX = "mdoc:"
private const val MAXIMUM_PROXIMITY_PAYLOAD_BYTES = 2_953
private const val QR_QUIET_ZONE_MODULES = 4
