package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.isError
import id.walt.walletdemo.compose.logic.isStatusBusy
import id.walt.walletdemo.compose.logic.shouldShowPersistentStatus
import id.walt.walletdemo.compose.logic.statusText
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun StatusCard(state: WalletDemoUiState, onDismiss: () -> Unit = {}) {
    if (!state.shouldShowPersistentStatus) return
    var expanded by remember(state.statusText) { mutableStateOf(false) }
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.statusText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag(WalletUiTestTags.Status),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
            )
            if (state.isError) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Less" else "More")
                    }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}
