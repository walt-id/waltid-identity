package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import id.walt.wallet2.mobile.ProximityUnsatisfiedRequest
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
internal fun ProximityUnsatisfiedRequestContent(request: ProximityUnsatisfiedRequest) {
    var visibleDocuments by remember(request) { mutableStateOf(5) }
    val documents = request.documents
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("proximity-unsatisfied-request")) {
        Text(stringResource(Res.string.proximity_reader_not_evaluated), style = MaterialTheme.typography.bodySmall)
        documents.take(visibleDocuments).forEach { document ->
            var visibleElements by remember(request, document.requestIndex) { mutableStateOf(20) }
            val elements = document.requestedElements
            ReviewMetadataSection(stringResource(Res.string.proximity_document_request, document.requestIndex + 1)) {
                Text(document.docType, style = MaterialTheme.typography.bodySmall)
                elements.take(visibleElements).forEach { element ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(humanizedElementIdentifier(element.elementIdentifier), fontWeight = FontWeight.SemiBold)
                        Text(element.namespace, style = MaterialTheme.typography.bodySmall)
                        if (element.intentToRetain) Text(stringResource(Res.string.proximity_reader_retention),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (elements.size > visibleElements) TextButton(onClick = { visibleElements += 20 }) {
                    Text(stringResource(Res.string.proximity_more_requested_fields))
                }
            }
        }
        if (documents.size > visibleDocuments) TextButton(onClick = { visibleDocuments += 5 }) {
            Text(stringResource(Res.string.proximity_more_requested_documents))
        }
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
        enabled = state.hostActionInProgress == null, testTag = WalletUiTestTags.ProximityDone)
    val failed = state.sessionState as? ProximityState.Failed
    val remediation = failed?.error?.remediationActions?.firstOrNull {
        it != ProximityRemediationAction.Retry && it != ProximityRemediationAction.UseSupportedDevice
    }
    when {
        remediation != null -> WalletActions(
            primary = WalletAction(hostActionForDisplay(remediation).label(), { onRemediate(remediation) },
                enabled = state.hostActionInProgress == null), secondary = done)
        failed?.error?.recovery == ProximityRecovery.StartNewSession -> WalletActions(
            primary = WalletAction(stringResource(Res.string.proximity_try_again), onRestart,
                testTag = WalletUiTestTags.ProximityRetry), secondary = done)
        else -> WalletActions(primary = done, secondary =
            if (state.sessionState is ProximityState.Completed && state.recentPlan?.isExpired == false)
                WalletAction(stringResource(Res.string.proximity_prepare_again), onReviewRecentRequest,
                    testTag = "proximity-prepare-again")
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
