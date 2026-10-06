package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
internal fun QrScannerDialog(
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WalletScreenHeader("Scan QR code") {
                    IconButton(onClick = onDismiss) {
                        WalletIcon(WalletSymbol.Decline, "Close scanner")
                    }
                }
                PlatformQrScanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .aspectRatio(3f / 4f),
                    onCodeScanned = onCodeScanned,
                )
            }
        }
    }
}

@Composable
internal expect fun PlatformQrScanner(
    modifier: Modifier,
    onCodeScanned: (String) -> Unit,
)
