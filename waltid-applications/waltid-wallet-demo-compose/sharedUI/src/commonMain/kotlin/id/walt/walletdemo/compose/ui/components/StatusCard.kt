package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.isError
import id.walt.walletdemo.compose.logic.isStatusBusy
import id.walt.walletdemo.compose.logic.statusText
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun StatusCard(state: WalletDemoUiState) {
    val containerColor = when {
        state.isError -> MaterialTheme.colorScheme.errorContainer
        state.isStatusBusy -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color(0xFFD8E2FF)
    }
    val contentColor = when {
        state.isError -> MaterialTheme.colorScheme.onErrorContainer
        state.isStatusBusy -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> Color(0xFF002E69)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Text(
            text = state.statusText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(WalletUiTestTags.Status),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
