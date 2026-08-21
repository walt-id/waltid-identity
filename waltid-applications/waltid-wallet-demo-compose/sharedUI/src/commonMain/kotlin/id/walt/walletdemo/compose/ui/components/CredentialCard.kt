package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toCardDisplayData

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CredentialCard(
    details: CredentialDetails,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    CredentialCardArt(
        art = details.toCardDisplayData().toCardArt(),
        compact = compact,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}
