@file:OptIn(ExperimentalWasmJsInterop::class)

package id.walt.walletdemo.compose.ui

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import id.walt.walletdemo.compose.ui.components.RasterImageSupport
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

internal actual fun walletImageDecoderFactory(): Decoder.Factory = BrowserImageDecoder.Factory()

/**
 * Decode rasters with the browser (`createImageBitmap`, then an `HTMLImageElement` fallback).
 *
 * Always claims the fetch result so Coil's Wasm worker Skia decoder never runs. Failures throw
 * [IllegalArgumentException] on the request coroutine and become Coil `ErrorResult`s.
 */
internal class BrowserImageDecoder(
    private val source: ImageSource,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().readByteArray()
        if (RasterImageSupport.looksLikeUnsupportedMarkup(bytes)) {
            throw IllegalArgumentException("Unsupported image")
        }
        val decoded = decodeBytesWithBrowser(bytes)
        val bitmap = decoded.toSkiaBitmap()
        bitmap.setImmutable()
        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = false,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder = BrowserImageDecoder(result.source)
    }
}

private class BrowserDecodedImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
)

private suspend fun decodeBytesWithBrowser(bytes: ByteArray): BrowserDecodedImage =
    suspendCancellableCoroutine { continuation ->
        startRasterDecode(
            bytes = bytes.toUint8Array(),
            mime = RasterImageSupport.guessMimeType(bytes),
            onSuccess = { decoded ->
                if (continuation.isActive) {
                    runCatching { decoded.toBrowserDecodedImage() }
                        .onSuccess(continuation::resume)
                        .onFailure {
                            continuation.resumeWithException(IllegalArgumentException("Failed to decode image"))
                        }
                }
            },
            onError = { message ->
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalArgumentException(message))
                }
            },
        )
    }

private fun JsAny.toBrowserDecodedImage(): BrowserDecodedImage {
    val width = decodedWidth(this)
    val height = decodedHeight(this)
    if (width <= 0 || height <= 0) {
        throw IllegalArgumentException("Failed to decode image")
    }
    return BrowserDecodedImage(
        width = width,
        height = height,
        pixels = decodedPixels(this).toKotlinByteArray(),
    )
}

private fun BrowserDecodedImage.toSkiaBitmap(): Bitmap {
    val imageInfo = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.RGBA_8888,
        alphaType = ColorAlphaType.UNPREMUL,
    )
    val bitmap = Bitmap()
    if (!bitmap.installPixels(imageInfo, pixels, imageInfo.minRowBytes)) {
        throw IllegalArgumentException("Failed to allocate image")
    }
    return bitmap
}

private fun ByteArray.toUint8Array(): Uint8Array {
    val array = Uint8Array(size)
    for (index in indices) {
        array[index] = this[index]
    }
    return array
}

private fun Uint8Array.toKotlinByteArray(): ByteArray {
    val result = ByteArray(length)
    for (index in result.indices) {
        result[index] = this[index]
    }
    return result
}

private fun startRasterDecode(
    bytes: Uint8Array,
    mime: String,
    onSuccess: (JsAny) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        const blob = new Blob([bytes], { type: mime });
        const fail = (err) => {
            const message = (err && err.message) ? err.message : "Failed to decode image";
            onError(message);
        };
        const finish = (source) => {
            try {
                const canvas = document.createElement("canvas");
                canvas.width = source.width;
                canvas.height = source.height;
                const context = canvas.getContext("2d");
                if (!context) {
                    fail(new Error("Failed to decode image"));
                    return;
                }
                context.drawImage(source, 0, 0);
                if (typeof source.close === "function") {
                    source.close();
                }
                const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
                onSuccess({
                    width: imageData.width,
                    height: imageData.height,
                    pixels: new Uint8Array(imageData.data.buffer),
                });
            } catch (err) {
                fail(err);
            }
        };
        createImageBitmap(blob).then(finish).catch(() => {
            const url = URL.createObjectURL(blob);
            const image = new Image();
            image.onload = () => {
                URL.revokeObjectURL(url);
                finish(image);
            };
            image.onerror = () => {
                URL.revokeObjectURL(url);
                fail(new Error("Failed to decode image"));
            };
            image.src = url;
        });
        """,
    )
}

private fun decodedWidth(decoded: JsAny): Int = js("decoded.width")

private fun decodedHeight(decoded: JsAny): Int = js("decoded.height")

private fun decodedPixels(decoded: JsAny): Uint8Array = js("decoded.pixels")
