package id.walt.walletdemo.compose.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.svg.SvgDecoder
import id.walt.walletdemo.compose.ui.components.RasterImageSupport
import kotlin.math.min
import okio.use

internal expect fun walletImageDecoderFactory(): Decoder.Factory

internal fun installWalletImageLoader(context: PlatformContext) {
    SingletonImageLoader.setSafe { imageContext ->
        ImageLoader.Builder(imageContext)
            .components {
                add(SvgDecoder.Factory())
                add(walletImageDecoderFactory())
            }
            .build()
    }
    SingletonImageLoader.get(context)
}

internal class RasterGateDecoderFactory : Decoder.Factory {
    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        val header = result.headerBytes() ?: return UnsupportedImageDecoder
        if (RasterImageSupport.looksLikeRaster(header)) return null
        return UnsupportedImageDecoder
    }
}

internal object UnsupportedImageDecoder : Decoder {
    override suspend fun decode() =
        throw IllegalArgumentException("Unsupported image")
}

internal fun SourceFetchResult.headerBytes(): ByteArray? =
    runCatching {
        source.source().peek().use { peek ->
            peek.request(64L)
            val length = min(64L, peek.buffer.size)
            if (length <= 0L) ByteArray(0) else peek.readByteArray(length)
        }
    }.getOrNull()
