package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformQrScanner(
    modifier: Modifier,
    onCodeScanned: (String) -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "QR scanning is unavailable in the web demo. Enter the URL manually.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
