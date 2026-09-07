package id.walt.walletdemo.compose.logic

import android.graphics.BitmapFactory

internal actual fun platformCanDecodeImage(bytes: ByteArray, maxPixelCount: Long): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false
    if (bounds.outWidth.toLong() * bounds.outHeight > maxPixelCount) return@runCatching false

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching false
    bitmap.recycle()
    true
}.getOrDefault(false)

private fun sampleSizeFor(width: Int, height: Int): Int {
    var sampleSize = 1
    while (width / sampleSize > validationImageSize || height / sampleSize > validationImageSize) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val validationImageSize = 64
