package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityError
import id.walt.wallet2.mobile.ProximityRecovery
import id.walt.wallet2.mobile.ProximityRemediationAction
import id.walt.wallet2.mobile.ProximityState
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.ui.components.ReviewMetadataSection
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import id.walt.walletdemo.compose.ui.components.WalletSymbol
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityProgressContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .testTag(WalletUiTestTags.ProximityStatus)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CircularProgressIndicator()
        Text(message, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ProximityTerminalContent(
    title: String,
    message: String,
    details: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message)
        details?.invoke()

    }
}

@Composable
internal fun ProximityFailedContent(error: ProximityError) {
    ReviewMetadataSection(stringResource(Res.string.proximity_failed_title)) {
        Text(error.message, modifier = Modifier.testTag(WalletUiTestTags.ProximityError))
    }
}

/** Keep terminal decisions reachable while the receipt or error details scroll. */
@Composable
internal fun ProximityOutcomeActions(
    state: WalletDemoProximityUiState,
    hostActionForDisplay: (ProximityRemediationAction) -> ProximityRemediationAction,
    onRemediate: (ProximityRemediationAction) -> Unit,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
    onReviewRecentRequest: () -> Unit,
) {
    val done = WalletAction(stringResource(Res.string.proximity_done), onDismiss,
        enabled = state.hostActionInProgress == null, testTag = WalletUiTestTags.ProximityDone, icon = WalletSymbol.Accept)
    val failed = state.sessionState as? ProximityState.Failed
    val remediation = failed?.error?.remediationActions?.firstOrNull {
        it != ProximityRemediationAction.Retry && it != ProximityRemediationAction.UseSupportedDevice
    }
    when {
        remediation != null -> WalletActions(
            primary = WalletAction(hostActionForDisplay(remediation).label(), { onRemediate(remediation) },
                enabled = state.hostActionInProgress == null, icon = WalletSymbol.Retry), secondary = done)
        failed?.error?.recovery == ProximityRecovery.StartNewSession -> WalletActions(
            primary = WalletAction(stringResource(Res.string.proximity_try_again), onRestart,
                testTag = WalletUiTestTags.ProximityRetry, icon = WalletSymbol.Retry), secondary = done)
        else -> WalletActions(primary = done, secondary =
            if (state.sessionState is ProximityState.Completed && state.recentPlan?.isExpired == false)
                WalletAction(stringResource(Res.string.proximity_prepare_again), onReviewRecentRequest,
                    testTag = "proximity-prepare-again", icon = WalletSymbol.Nearby)
            else null)
    }
}

@Composable
internal fun ProximityErrorCard(error: ProximityError) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.ProximityError)
            .semantics { liveRegion = LiveRegionMode.Assertive }
    ) {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(Res.string.proximity_action_failed), fontWeight = FontWeight.SemiBold)
            Text(error.message)
        }
    }
}
