package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.WalletBrandBlue

internal val WalletButtonShape = RoundedCornerShape(8.dp)

@Composable
internal fun walletPrimaryButtonColors(): ButtonColors = if (isSystemInDarkTheme()) {
    ButtonDefaults.buttonColors(
        containerColor = Color(0xFF8CB8FF),
        contentColor = Color(0xFF002E69),
        disabledContainerColor = Color(0xFF334155),
        disabledContentColor = Color(0xFFCBD5E1),
    )
} else {
    ButtonDefaults.buttonColors(
        containerColor = WalletBrandBlue,
        contentColor = Color.White,
        disabledContainerColor = Color(0xFF94A3B8),
        disabledContentColor = Color.White,
    )
}

@Composable
internal fun walletSecondaryButtonColors(): ButtonColors = if (isSystemInDarkTheme()) {
    ButtonDefaults.outlinedButtonColors(
        containerColor = Color(0xFF0F172A),
        contentColor = Color(0xFFF8FAFC),
        disabledContainerColor = Color.Transparent,
        disabledContentColor = Color(0xFF64748B),
    )
} else {
    ButtonDefaults.outlinedButtonColors(
        containerColor = Color.White,
        contentColor = WalletBrandBlue,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = Color(0xFF94A3B8),
    )
}

@Composable
internal fun walletSecondaryButtonBorder(enabled: Boolean): BorderStroke = BorderStroke(
    width = 1.dp,
    color = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant
        isSystemInDarkTheme() -> Color(0xFF475569)
        else -> Color(0xFFCBD5E1)
    },
)

@Composable
internal fun WalletPrimaryButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = WalletButtonShape,
        colors = walletPrimaryButtonColors(),
        contentPadding = contentPadding,
        modifier = modifier.defaultMinSize(minHeight = if (compact) 40.dp else 48.dp),
        content = { content() },
    )
}

@Composable
internal fun WalletSecondaryButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = WalletButtonShape,
        colors = walletSecondaryButtonColors(),
        border = walletSecondaryButtonBorder(enabled),
        contentPadding = contentPadding,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        content = { content() },
    )
}
