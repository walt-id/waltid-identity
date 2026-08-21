package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun CredentialOverviewSection(
    details: CredentialDetails,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null,
) {
    val display = details.toCardDisplayData()
    val issuerFallback = details.summary.issuer?.takeIf { it.isNotBlank() } ?: display.issuer

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.credentialOverview(display.id)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CredentialCardArt(
            art = display.toCardArt(),
            compact = true,
            modifier = if (onCardClick != null) Modifier.clickable(onClick = onCardClick) else Modifier,
        )

        val issuerDisplay = details.issuerDisplay
        if (issuerDisplay != null) {
            MetadataIdentityRow(
                display = issuerDisplay,
                fallbackName = issuerFallback,
                supportingText = details.summary.issuer
                    ?.takeIf { it.isNotBlank() && it != issuerDisplay.name },
            )
        } else {
            Text(
                "Issuer: $issuerFallback",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
