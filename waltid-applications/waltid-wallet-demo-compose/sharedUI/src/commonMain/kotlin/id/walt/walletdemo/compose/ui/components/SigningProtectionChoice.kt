package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtection

@Composable
internal fun SigningProtectionChoice(
    protection: WalletDemoSigningProtection,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .testTag(testTag)
            .alpha(if (enabled) 1f else 0.6f)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(protection.title(), style = MaterialTheme.typography.bodyLarge)
            Text(
                protection.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun WalletDemoSigningProtection.title(): String = when (this) {
    WalletDemoSigningProtection.None -> "No biometric signing"
    WalletDemoSigningProtection.Biometric -> "Biometric signing"
}

internal fun WalletDemoSigningProtection.description(): String = when (this) {
    WalletDemoSigningProtection.None -> "Private-key operations do not require biometric authorization."
    WalletDemoSigningProtection.Biometric ->
        "Strong biometric authorization can be reused for signing for 10 seconds."
}
