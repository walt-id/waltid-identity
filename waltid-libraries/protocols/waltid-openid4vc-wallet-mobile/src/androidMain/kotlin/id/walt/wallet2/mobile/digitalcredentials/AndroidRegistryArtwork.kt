package id.walt.wallet2.mobile.digitalcredentials

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

internal fun ByteArray.decodeRegistryIcon(): Bitmap? {
    if (!isImageBytes(this)) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    val width = bounds.outWidth.toLong()
    val height = bounds.outHeight.toLong()
    if (width <= 0L || height <= 0L) return null
    if (width * height > REGISTRY_ICON_MAX_PIXELS) return null
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size) ?: return null
    return bitmap.scaleToMaxEdge(REGISTRY_ICON_MAX_EDGE_PX)
}

internal fun Bitmap.toPngBytes(): ByteArray =
    ByteArrayOutputStream().use { out ->
        compress(CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

private fun Bitmap.scaleToMaxEdge(maxEdgePx: Int): Bitmap {
    val longestEdge = maxOf(width, height)
    if (longestEdge <= maxEdgePx) return this
    val scale = maxEdgePx.toFloat() / longestEdge.toFloat()
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

internal fun loadApplicationIcon(context: Context): Bitmap {
    val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
    return drawable.toBitmap(maxEdgePx = REGISTRY_ICON_MAX_EDGE_PX)
}

private fun Drawable.toBitmap(maxEdgePx: Int): Bitmap {
    val source = when {
        this is BitmapDrawable && bitmap != null && !bitmap.isRecycled -> bitmap
        else -> {
            val width = intrinsicWidth.takeIf { it > 0 } ?: maxEdgePx
            val height = intrinsicHeight.takeIf { it > 0 } ?: maxEdgePx
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                setBounds(0, 0, canvas.width, canvas.height)
                draw(canvas)
            }
        }
    }
    return source.scaleToMaxEdge(maxEdgePx)
}

/** Credential Manager selector icons are small; keep registry PNG payloads modest. */
private const val REGISTRY_ICON_MAX_EDGE_PX = 128

private const val REGISTRY_ICON_MAX_PIXELS = 2_048L * 2_048L
