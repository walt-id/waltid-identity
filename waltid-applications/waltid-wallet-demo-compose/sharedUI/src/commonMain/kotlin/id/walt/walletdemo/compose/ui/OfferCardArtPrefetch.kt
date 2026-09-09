package id.walt.walletdemo.compose.ui

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.ui.components.RasterImageSupport

/**
 * Downloads offered-credential background images into the Coil cache before Review is shown.
 *
 * The receive spinner already covers this wait so [CredentialCardArt] can paint issuer art on the
 * first Review frame instead of flashing constructed fallback art.
 */
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
            runCatching {
                loader.execute(ImageRequest.Builder(context).data(uri).build())
            }
        }
}
