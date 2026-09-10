package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoProximityApprovalMode
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.proximity_ask_each_time_description
import id.walt.walletdemo.compose.ui.resources.proximity_prepare_sharing
import id.walt.walletdemo.compose.ui.resources.proximity_prepare_sharing_description
import id.walt.walletdemo.compose.ui.resources.proximity_prepare_sharing_short_description
import id.walt.walletdemo.compose.ui.resources.proximity_ask_each_time_short_description
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityApprovalModeChoice(
    selected: WalletDemoProximityApprovalMode,
    onSelect: (WalletDemoProximityApprovalMode) -> Unit,
    compact: Boolean = false,
) {
    val prepared = selected == WalletDemoProximityApprovalMode.PrepareSharing
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = prepared, role = Role.Switch) {
                onSelect(if (it) WalletDemoProximityApprovalMode.PrepareSharing else WalletDemoProximityApprovalMode.AskEachTime)
            }
            .testTag("proximity-approval-mode"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.proximity_prepare_sharing), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(when {
                    compact && prepared -> Res.string.proximity_prepare_sharing_short_description
                    compact -> Res.string.proximity_ask_each_time_short_description
                    prepared -> Res.string.proximity_prepare_sharing_description
                    else -> Res.string.proximity_ask_each_time_description
                }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = prepared, onCheckedChange = null)
    }
}
