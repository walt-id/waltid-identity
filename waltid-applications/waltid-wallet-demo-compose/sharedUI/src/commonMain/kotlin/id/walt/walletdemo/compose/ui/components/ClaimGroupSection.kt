package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.ClaimGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun ClaimGroupSection(group: ClaimGroup, modifier: Modifier = Modifier) {
    if (group.items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.claimGroup(group.title)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            group.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        group.items.forEach { item ->
            ClaimValueRow(
                item = item,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        HorizontalDivider()
    }
}
