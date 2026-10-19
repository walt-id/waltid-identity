package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityCapabilities
import id.walt.wallet2.mobile.ProximityRemediationAction
import id.walt.walletdemo.compose.ui.components.ReviewMetadataSection
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import id.walt.walletdemo.compose.ui.components.WalletSymbol
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityPrerequisiteContent(
    capabilities: ProximityCapabilities,
    hostActionInProgress: ProximityRemediationAction?,
    hostActionForDisplay: (ProximityRemediationAction) -> ProximityRemediationAction,
    onRetry: () -> Unit,
    onContinueWithAvailableConnection: () -> Unit,
    onRemediate: (ProximityRemediationAction) -> Unit,
) {
    val action = capabilities.remediationActions.firstOrNull { it != ProximityRemediationAction.UseSupportedDevice }
    val message = listOf(capabilities.nfcEngagement, capabilities.qrEngagement, capabilities.bluetoothLowEnergy,
        capabilities.nfcRetrieval, capabilities.nfcV2Retrieval, capabilities.wifiAwareRetrieval)
        .firstOrNull { it.selected && action in it.remediationActions }?.unavailable?.message
        ?: capabilities.selectedUnavailableMessage ?: stringResource(Res.string.proximity_generic_unavailable)
    ReviewMetadataSection(title = action?.let(hostActionForDisplay)?.label() ?: stringResource(Res.string.proximity_action_needed)) {
        Text(message)
        if (hostActionInProgress != null) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        WalletActions(
            primary = WalletAction(action?.let(hostActionForDisplay)?.label() ?: stringResource(Res.string.proximity_check_again),
                onClick = { if (action != null) onRemediate(action) else onRetry() },
                enabled = hostActionInProgress == null, testTag = if (action == null) WalletUiTestTags.ProximityRetry else null),
            secondary = if (capabilities.mayStart) WalletAction(stringResource(Res.string.proximity_continue_available),
                onContinueWithAvailableConnection, enabled = hostActionInProgress == null) else null,
        )
    }
}

private val ProximityCapabilities.selectedUnavailableMessage: String?
    get() = listOf(
        nfcEngagement,
        bluetoothLowEnergy,
        nfcRetrieval,
        nfcV2Retrieval,
        qrEngagement,
        wifiAwareRetrieval,
    ).firstNotNullOfOrNull { capability -> capability.unavailable?.message.takeIf { capability.selected } }
