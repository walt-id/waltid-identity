package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
            group.title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                group.items.forEachIndexed { index, item ->
                    if (index > 0) ClaimRowDivider()
                    ClaimValueRow(item = item)
                }
            }
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
