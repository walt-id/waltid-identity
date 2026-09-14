package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.waltid_logo
import org.jetbrains.compose.resources.painterResource

@Composable
internal actual fun DefaultWaltLogo(modifier: Modifier) {
    Image(
        painter = painterResource(Res.drawable.waltid_logo),
        contentDescription = "walt.id",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
