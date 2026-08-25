package id.walt.walletdemo.compose.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

private const val ComposeResourceAssetPath =
    "composeResources/id.walt.walletdemo.compose.ui.resources/drawable/waltid_logo.png"

private object WaltLogoResource

@Composable
internal actual fun DefaultWaltLogo(modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(context) { loadWaltLogoBitmap(context) } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "walt.id",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

private fun loadWaltLogoBitmap(context: Context): Bitmap? {
    val resourceId = listOf(context.packageName, "id.walt.walletdemo.compose.ui")
        .firstNotNullOfOrNull { packageName ->
            context.resources.getIdentifier("waltid_logo", "drawable", packageName).takeIf { it != 0 }
        }
    if (resourceId != null) {
        BitmapFactory.decodeResource(context.resources, resourceId)?.let { return it }
    }
    runCatching {
        context.assets.open(ComposeResourceAssetPath).use(BitmapFactory::decodeStream)
    }.getOrNull()?.let { return it }
    return runCatching {
        WaltLogoResource::class.java.classLoader
            ?.getResourceAsStream(ComposeResourceAssetPath)
            ?.use(BitmapFactory::decodeStream)
    }.getOrNull()
}
