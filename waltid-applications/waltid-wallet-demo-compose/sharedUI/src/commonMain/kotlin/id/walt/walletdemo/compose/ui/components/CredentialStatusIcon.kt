package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus

@Composable
internal fun CredentialStatusIcon(
    status: WalletDemoCredentialStatus,
    modifier: Modifier = Modifier,
    description: String? = null,
    contrast: Boolean = false,
    size: Dp = 28.dp,
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
        Box(modifier.size(size)
            .then(if (contrast) Modifier.background(MaterialTheme.colorScheme.surface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center) {
            WalletIcon(symbol, description, Modifier.size(size * (22f / 28f))
                .offset(y = if (contrast && symbol == WalletSymbol.StatusWarning) -size * (2f / 28f) else 0.dp))
        }
    }
}
