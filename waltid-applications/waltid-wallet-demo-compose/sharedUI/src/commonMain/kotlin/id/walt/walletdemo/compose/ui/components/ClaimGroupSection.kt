package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.ClaimGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun ClaimGroupSection(
    group: ClaimGroup,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
) {
    if (group.items.isEmpty()) return

    WalletSection(
        title = if (collapsible) null else group.title,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (collapsible) {
                MetadataDisclosure(
                    title = group.title,
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
}

@Composable
private fun ClaimGroupItems(group: ClaimGroup) {
    group.items.forEachIndexed { index, item ->
        if (index > 0) MetadataRowDivider()
        key(item.path.id) { ClaimValueRow(item = item) }
    }
}
