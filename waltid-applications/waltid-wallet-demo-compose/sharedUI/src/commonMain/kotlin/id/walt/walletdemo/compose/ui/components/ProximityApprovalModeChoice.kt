package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletDemoProximityApprovalMode
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityApprovalModeChoice(
    selected: WalletDemoProximityApprovalMode,
    onSelect: (WalletDemoProximityApprovalMode) -> Unit,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    if (compact) {
        Row(Modifier.selectableGroup().testTag("proximity-approval-mode")) {
            WalletDemoProximityApprovalMode.entries.forEach { mode ->
                val prepared = mode == WalletDemoProximityApprovalMode.PrepareSharing
                Row(Modifier.weight(1f).heightIn(min = 48.dp)
                    .selectable(selected = selected == mode, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(mode) })
                    .testTag(if (prepared) "proximity-approval-prepare" else "proximity-approval-ask"),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == mode, onClick = null, enabled = enabled)
                    Text(stringResource(if (prepared) Res.string.proximity_prepare_sharing else Res.string.proximity_ask_each_time),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        return
    }
    Column(Modifier.selectableGroup().testTag("proximity-approval-mode")) {
        WalletDemoProximityApprovalMode.entries.forEachIndexed { index, mode ->
            if (index > 0) SettingsDivider()
            val prepared = mode == WalletDemoProximityApprovalMode.PrepareSharing
            SettingsChoiceRow(
                title = stringResource(if (prepared) Res.string.proximity_prepare_sharing else Res.string.proximity_ask_each_time),
                detail = stringResource(when {
                    compact && prepared -> Res.string.proximity_prepare_sharing_short_description
                    compact -> Res.string.proximity_ask_each_time_short_description
                    prepared -> Res.string.proximity_prepare_sharing_description
                    else -> Res.string.proximity_ask_each_time_description
                }),
                selected = selected == mode,
                onSelect = { onSelect(mode) },
                enabled = enabled,
                modifier = Modifier.testTag(if (prepared) "proximity-approval-prepare" else "proximity-approval-ask"),
            )
        }
    }
}
