package id.walt.walletdemo.compose.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import id.walt.walletdemo.compose.logic.QrCodePayload

internal actual fun encodeQrCode(payload: QrCodePayload): ImageBitmap =
    error("QR code rendering is only available in the mobile wallet")
