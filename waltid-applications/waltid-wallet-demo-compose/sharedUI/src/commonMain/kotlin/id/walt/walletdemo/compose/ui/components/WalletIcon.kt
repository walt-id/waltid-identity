package id.walt.walletdemo.compose.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.painterResource

/** App actions only. Transaction field display remains controlled by its verified metadata. */
internal enum class WalletSymbol {
    Back, Next, Accept, Decline, Share, Receive, Retry, Scan, Paste, Lock, Key, Nearby, Info, Delete,
}

/** Pass null beside a text label so assistive technology announces the action only once. */
@Composable
internal fun WalletIcon(symbol: WalletSymbol, contentDescription: String?, modifier: Modifier = Modifier) {
    val painter = when (symbol) {
        WalletSymbol.Scan -> painterResource(Res.drawable.proximity_qr)
        WalletSymbol.Paste -> painterResource(Res.drawable.settings_copy)
        WalletSymbol.Receive -> painterResource(Res.drawable.settings_import)
        WalletSymbol.Nearby -> painterResource(Res.drawable.settings_nearby)
        WalletSymbol.Key -> painterResource(Res.drawable.settings_key)
        else -> rememberVectorPainter(when (symbol) {
            WalletSymbol.Back -> Icons.AutoMirrored.Filled.ArrowBack
            WalletSymbol.Next -> Icons.AutoMirrored.Filled.ArrowForward
            WalletSymbol.Accept -> Icons.Filled.Check
            WalletSymbol.Decline -> Icons.Filled.Close
            WalletSymbol.Share -> Icons.AutoMirrored.Filled.Send
            WalletSymbol.Retry -> Icons.Filled.Refresh
            WalletSymbol.Lock -> Icons.Filled.Lock
            WalletSymbol.Info -> Icons.Filled.Info
            WalletSymbol.Delete -> Icons.Filled.Delete
        })
    }
    Icon(painter, contentDescription, modifier)
}
