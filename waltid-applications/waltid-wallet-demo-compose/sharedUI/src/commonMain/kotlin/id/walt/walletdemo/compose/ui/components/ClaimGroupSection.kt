package id.walt.walletdemo.compose.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.ClaimGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun ClaimGroupSection(group: ClaimGroup, modifier: Modifier = Modifier) {
    if (group.items.isEmpty()) return

    ReviewMetadataSection(
        title = group.title,
        modifier = modifier.testTag(WalletUiTestTags.claimGroup(group.title)),
    ) {
        group.items.forEachIndexed { index, item ->
            if (index > 0) ClaimRowDivider()
            ClaimValueRow(item = item)
        }
    }
}

@Composable
private fun ClaimRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
    )
}
