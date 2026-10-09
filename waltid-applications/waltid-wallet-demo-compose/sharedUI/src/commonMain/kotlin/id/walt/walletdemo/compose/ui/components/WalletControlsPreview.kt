package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.WalletDemoTheme

/** A small, interactive fixture shared by IDE previews and the visual catalogue. No wallet is created. */
@Composable
internal fun WalletControlsPreview() {
    var count by remember { mutableStateOf(1) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Actions and counts", style = MaterialTheme.typography.titleLarge)
        WalletActions(
            primary = WalletAction("Continue", {}),
            secondary = WalletAction("Cancel", {}),
        )
        WalletActions(
            primary = WalletAction("Working…", {}, enabled = false),
            secondary = WalletAction("Cancel", {}, enabled = false),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Copies: $count")
            WalletCountControl(count, 1..3, true, "Fewer copies", "More copies",
                "preview.copies.less", "preview.copies.more", { count = it })
        }
    }
}

@Preview(name = "Wallet controls", widthDp = 393)
@Composable
private fun ControlsPreview() = WalletDemoTheme { Surface { WalletControlsPreview() } }

@Preview(name = "Wallet controls · RTL", widthDp = 320, fontScale = 1.5f)
@Composable
private fun ControlsRtlPreview() = CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    WalletDemoTheme { Surface { WalletControlsPreview() } }
}
