package id.walt.walletdemo.compose.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import id.walt.walletdemo.compose.logic.QrCodePayload

internal expect fun encodeQrCode(payload: QrCodePayload): ImageBitmap
