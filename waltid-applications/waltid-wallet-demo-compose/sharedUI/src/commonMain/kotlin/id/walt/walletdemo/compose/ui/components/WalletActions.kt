package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Intrinsic-width decisions in reading order, wrapping when their labels need more space. */
@Composable
internal fun WalletActions(
    primary: WalletAction,
    secondary: WalletAction? = null,
    tertiary: WalletAction? = null,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tertiary?.let { action ->
            TextButton(onClick = action.onClick, enabled = action.enabled, modifier = action.modifier()) { ActionLabel(action) }
        }
        secondary?.let { action ->
            OutlinedButton(onClick = action.onClick, enabled = action.enabled,
                modifier = action.modifier(), shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) { ActionLabel(action) }
        }
        Button(onClick = primary.onClick, enabled = primary.enabled,
            modifier = primary.modifier(), shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) { ActionLabel(primary) }
    }
}

private fun WalletAction.modifier(): Modifier = Modifier.heightIn(min = 48.dp)
    .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)

@Composable
private fun RowScope.ActionLabel(action: WalletAction) {
    action.icon?.let {
        WalletIcon(it, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(action.label, modifier = Modifier.weight(1f, fill = false))
}
