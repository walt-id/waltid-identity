package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.ClaimGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun ClaimGroupSection(
    group: ClaimGroup,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
) {
    if (group.items.isEmpty()) return

    ReviewMetadataSection(
        title = group.title,
        modifier = modifier,
    ) {
        if (collapsible) {
            MetadataDisclosure(
                title = "${group.items.size} ${if (group.items.size == 1) "entry" else "entries"}",
                initiallyExpanded = group.initiallyExpanded,
                modifier = Modifier.testTag(WalletUiTestTags.claimGroup(group.title)),
            ) {
                ClaimGroupItems(group)
            }
        } else {
            ClaimGroupItems(group)
        }
    }
}

@Composable
private fun ClaimGroupItems(group: ClaimGroup) {
    group.items.forEachIndexed { index, item ->
        if (index > 0) MetadataRowDivider()
        ClaimValueRow(item = item)
    }
}
