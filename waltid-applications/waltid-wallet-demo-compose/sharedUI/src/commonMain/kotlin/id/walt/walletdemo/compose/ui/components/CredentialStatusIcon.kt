package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus

@Composable
internal fun CredentialStatusIcon(
    status: WalletDemoCredentialStatus,
    modifier: Modifier = Modifier,
    description: String? = null,
    contrast: Boolean = false,
) {
    val dark = isSystemInDarkTheme()
    val color = when {
        status.red -> MaterialTheme.colorScheme.error
        status.positive -> if (dark) Color(0xFF81C995) else Color(0xFF216E39)
        status.hasIssue -> if (dark) Color(0xFFFFCC80) else Color(0xFF946000)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val symbol = when {
        status.red -> WalletSymbol.StatusAdverse
        status.positive -> WalletSymbol.StatusClear
        status.hasIssue -> WalletSymbol.StatusWarning
        else -> WalletSymbol.Info
    }
    CompositionLocalProvider(LocalContentColor provides color) {
        WalletIcon(symbol, description, modifier.size(28.dp)
            .then(if (contrast) Modifier.background(MaterialTheme.colorScheme.surface, CircleShape) else Modifier)
            .padding(3.dp))
    }
}
