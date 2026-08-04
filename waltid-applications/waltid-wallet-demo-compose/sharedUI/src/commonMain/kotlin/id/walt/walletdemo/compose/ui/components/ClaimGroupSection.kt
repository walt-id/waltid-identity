package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.ClaimGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun ClaimGroupSection(group: ClaimGroup, modifier: Modifier = Modifier) {
    if (group.items.isEmpty()) return

    ReviewMetadataSection(
        title = group.title,
        modifier = modifier,
    ) {
        MetadataDisclosure(
            title = "${group.items.size} ${if (group.items.size == 1) "entry" else "entries"}",
            initiallyExpanded = group.initiallyExpanded,
            modifier = Modifier.testTag(WalletUiTestTags.claimGroup(group.title)),
        ) {
            group.items.forEachIndexed { index, item ->
                if (index > 0) MetadataRowDivider()
                ClaimValueRow(item = item)
            }
        }
    }
}
