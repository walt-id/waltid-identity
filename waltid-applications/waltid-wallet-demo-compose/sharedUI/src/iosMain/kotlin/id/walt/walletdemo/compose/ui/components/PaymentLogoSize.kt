package id.walt.walletdemo.compose.ui.components

import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use

internal actual fun paymentLogoHasSafeSize(bytes: ByteArray): Boolean = runCatching {
    // Skia's encoded image reads dimensions before allocating decoded pixel storage.
    Image.makeFromEncoded(bytes).use { it.width in 1..2048 && it.height in 1..2048 }
}.getOrDefault(false)
