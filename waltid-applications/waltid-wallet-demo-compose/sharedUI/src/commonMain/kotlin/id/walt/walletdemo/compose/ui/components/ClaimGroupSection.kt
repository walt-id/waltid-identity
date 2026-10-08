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
import id.walt.walletdemo.compose.logic.ClaimItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun ClaimGroupSection(
    group: ClaimGroup,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
    claimStatus: (ClaimItem) -> String? = { null },
) {
    if (group.items.isEmpty()) return

    WalletSection(
        title = if (collapsible) null else group.title,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = if (collapsible) 4.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (collapsible) {
                MetadataDisclosure(
                    title = group.title,
                    initiallyExpanded = group.initiallyExpanded,
                    modifier = Modifier.testTag(WalletUiTestTags.claimGroup(group.title)),
                ) {
                    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ClaimGroupItems(group, claimStatus)
                    }
                }
            } else {
                ClaimGroupItems(group, claimStatus)
            }
        }
    }
}

@Composable
private fun ClaimGroupItems(group: ClaimGroup, claimStatus: (ClaimItem) -> String?) {
    group.items.forEachIndexed { index, item ->
        if (index > 0) MetadataRowDivider()
        key(item.path.id) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ClaimValueRow(item = item)
                claimStatus(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
