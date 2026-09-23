package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.waltid_logo
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.setResourceReaderAndroidContext

@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun DefaultWaltLogo(modifier: Modifier) {
    val context = LocalContext.current
    remember(context) { setResourceReaderAndroidContext(context.applicationContext) }
    Image(
        painter = painterResource(Res.drawable.waltid_logo),
        contentDescription = "walt.id",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
