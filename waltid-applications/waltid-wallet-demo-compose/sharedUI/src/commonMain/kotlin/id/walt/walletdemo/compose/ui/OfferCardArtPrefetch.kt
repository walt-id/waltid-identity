package id.walt.walletdemo.compose.ui

import kotlinx.coroutines.CancellationException
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.BlackholeDecoder
import coil3.request.CachePolicy
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.ui.components.RasterImageSupport

/** Warms the encoded disk cache. The visible card selects its own decode size. */
@OptIn(ExperimentalCoilApi::class)
suspend fun prefetchOfferCardArt(
    context: PlatformContext,
    preview: WalletDemoOfferPreview,
) {
    val loader = SingletonImageLoader.get(context)
    preview.offeredCredentials
        .mapNotNull { credential ->
            credential.display?.backgroundImageUri?.takeIf(
                RasterImageSupport::isHttpsDisplayImageUrl,
            )
        }
        .distinct()
        .forEach { uri ->
            val request = ImageRequest.Builder(context)
                .data(uri)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .decoderFactory(BlackholeDecoder.Factory())
                .build()
            try {
                loader.execute(request)
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Exception) {
                // Optional artwork falls back to the credential color or logo.
            }
        }
}
