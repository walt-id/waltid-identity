package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

@Composable
internal actual fun DefaultWaltLogo(modifier: Modifier) {
    val context = LocalContext.current
    val resId = remember(context) {
        context.resources.getIdentifier("waltid_logo", "drawable", context.packageName)
    }
    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = "walt.id",
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}
