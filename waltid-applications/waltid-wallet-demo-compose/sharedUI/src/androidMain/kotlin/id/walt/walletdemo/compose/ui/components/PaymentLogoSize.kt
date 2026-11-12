package id.walt.walletdemo.compose.ui.components

import android.graphics.BitmapFactory

internal actual fun paymentLogoHasSafeSize(bytes: ByteArray): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    bounds.outMimeType in setOf("image/png", "image/jpeg") && bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048
}.getOrDefault(false)
