package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletDemoKeySetupStep
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** The same facts appear before creation and in Settings; only a draft configuration is editable. */
@Composable
internal fun SigningKeySummary(
    recovery: String,
    storage: String,
    approval: String,
    enabled: Boolean = true,
    onEdit: ((WalletDemoKeySetupStep) -> Unit)? = null,
) {
    val rows = listOf(
        Triple(WalletDemoKeySetupStep.Recovery, stringResource(Res.string.setup_recovery), recovery),
        Triple(WalletDemoKeySetupStep.Storage, stringResource(Res.string.setup_storage), storage),
        Triple(WalletDemoKeySetupStep.Approval, stringResource(Res.string.setup_approval), approval),
    )
    rows.forEachIndexed { index, (step, title, value) ->
        if (index > 0) SettingsDivider()
        if (onEdit == null) SettingsDetailRow(title, value)
        else WalletNavigationRow(title, onClick = { onEdit(step) }, summary = value,
            modifier = Modifier.testTag("wallet.keySetupEdit.${step.name}"), enabled = enabled)
    }
}
