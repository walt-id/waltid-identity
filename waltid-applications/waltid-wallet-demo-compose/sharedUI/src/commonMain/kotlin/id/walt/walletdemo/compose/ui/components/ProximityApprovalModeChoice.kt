package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoProximityApprovalMode
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Off retains Ask each time. Enabling preparation never approves a request by itself. */
@Composable
internal fun ProximityApprovalModeChoice(
    selected: WalletDemoProximityApprovalMode,
    onSelect: (WalletDemoProximityApprovalMode) -> Unit,
    compact: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val prepared = selected == WalletDemoProximityApprovalMode.PrepareSharing
    Box(modifier.testTag("proximity-approval-mode")) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("proximity-approval-prepare")
        .toggleable(prepared, enabled = enabled, role = Role.Checkbox,
            onValueChange = { onSelect(if (it) WalletDemoProximityApprovalMode.PrepareSharing else WalletDemoProximityApprovalMode.AskEachTime) })
        .padding(horizontal = if (compact) 0.dp else 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(prepared, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.proximity_prepare_sharing), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(if (compact) Res.string.proximity_prepare_sharing_short_description else Res.string.proximity_prepare_sharing_description),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
}
