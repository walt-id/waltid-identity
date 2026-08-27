package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toCardDisplayData

@Composable
internal fun CredentialCard(
    details: CredentialDetails,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    CredentialCardArt(
        art = details.toCardDisplayData().toCardArt(),
        compact = compact,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
