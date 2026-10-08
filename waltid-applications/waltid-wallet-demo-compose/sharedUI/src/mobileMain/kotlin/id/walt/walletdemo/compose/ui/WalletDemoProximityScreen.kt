package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityActionType
import id.walt.wallet2.mobile.ProximityElementReference
import id.walt.wallet2.mobile.ProximityEngagementMethod
import id.walt.wallet2.mobile.ProximityRemediationAction
import id.walt.wallet2.mobile.ProximityReview
import id.walt.wallet2.mobile.ProximityReviewReason
import id.walt.wallet2.mobile.ProximityState
import id.walt.wallet2.mobile.legalActions
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoProximityApprovalMode
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.ui.components.MetadataDisclosure
import id.walt.walletdemo.compose.ui.components.ReviewActionPresentation
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.SharingActionsRow
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import id.walt.walletdemo.compose.ui.components.WalletFooter
import id.walt.walletdemo.compose.ui.components.WalletSymbol
import id.walt.walletdemo.compose.ui.components.rememberSuccessDismissal
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WalletDemoProximityScreen(
    state: WalletDemoProximityUiState,
    credentialDetailsById: Map<String, CredentialDetails>,
    hostActions: WalletDemoProximityHostActionExecutor,
    hostActionForDisplay: (ProximityRemediationAction) ->
        ProximityRemediationAction = { it },
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, ProximityElementReference) -> Unit,
    onContinueAfterResponseChange: (Boolean) -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onRetry: () -> Unit,
    onRemediate: (ProximityRemediationAction, WalletDemoProximityHostActionExecutor) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
    onShowEngagement: (ProximityEngagementMethod) -> Unit = {},
    onContinueWithAvailableConnection: () -> Unit = {},
    onApprovalModeChange: (WalletDemoProximityApprovalMode) -> Unit = {},
    onReviewRecentRequest: () -> Unit = {},
    onConnectionOptions: (() -> Unit)? = null,
    headerOwnsClose: Boolean = false,
) {
    val sessionState = state.sessionState
    val terminal = state.isTerminal
    val canCancel = !state.closing && !terminal && (
        sessionState == null || ProximityActionType.Cancel in sessionState.legalActions
    )
    val screenTitle = stringResource(Res.string.proximity_in_person_title)
    SystemBackHandler(
        enabled = !state.closing && state.review == null && (terminal || canCancel),
    ) {
        if (terminal) onDismiss() else onCancel()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(rememberSuccessDismissal(key = sessionState ?: "nearby-preparing",
                enabled = sessionState is ProximityState.Completed && !sessionState.declined && sessionState.receipt != null
                    && !state.closing && state.actionError == null && state.hostActionInProgress == null,
                onDone = onDismiss))
            .testTag(WalletUiTestTags.ProximityScreen)
            .semantics { paneTitle = screenTitle },
    ) {
        val review = state.review
        if (state.closing) {
            ReviewScaffold(feedback = { ProximityProgressContent(stringResource(Res.string.proximity_terminating)) }) {}
        } else if (review != null) {
            WalletDemoProximityReview(
                state = state,
                review = review,
                credentialDetailsById = credentialDetailsById,
                onSelectCredential = onSelectCredential,
                onToggleElement = onToggleElement,
                onContinueAfterResponseChange = onContinueAfterResponseChange,
                onApprove = onApprove,
                onDecline = onDecline,
                onCancel = onCancel,
                onRestart = onRestart,
                headerOwnsClose = headerOwnsClose,
            )
        } else if (state.showsEngagement) {
            Box(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 8.dp)) {
                ProximityEngagementContent(state, credentialDetailsById, onShowEngagement, onApprovalModeChange, onConnectionOptions)
            }
            if ((canCancel && !headerOwnsClose) || state.actionError != null) {
                WalletFooter(feedback = state.actionError?.let { error -> { ProximityErrorCard(error) } },
                    actions = if (canCancel && !headerOwnsClose) ({ ProximityCancelAction(onCancel) }) else null)
            }
        } else {
            ReviewScaffold(
                feedback = state.actionError?.takeUnless { it == (sessionState as? ProximityState.Failed)?.error }
                    ?.let { error -> { ProximityErrorCard(error) } },
                actions = {
                    when {
                        canCancel && !headerOwnsClose -> ProximityCancelAction(onCancel)
                        state.isTerminal -> ProximityOutcomeActions(state, hostActionForDisplay,
                            onRemediate = { onRemediate(it, hostActions) }, onDismiss = onDismiss,
                            onRestart = onRestart, onReviewRecentRequest = onReviewRecentRequest)
                    }
                },
            ) {
                onConnectionOptions?.let { options ->
                    id.walt.walletdemo.compose.ui.components.WalletSection {
                        id.walt.walletdemo.compose.ui.components.WalletNavigationRow("Connection options", options, Modifier.testTag("proximity-connection-options"))
                    }
                }
                state.preparedSharing?.takeIf { sessionState is ProximityState.CheckingPrerequisites ||
                    sessionState is ProximityState.Preparing || sessionState is ProximityState.EngagementReady ||
                    sessionState is ProximityState.Connecting || sessionState is ProximityState.AwaitingRequest }?.let {
                    ProximityPreparedSharingSummary(it, credentialDetailsById)
                }
                when (sessionState) {
                    null -> ProximityProgressContent(stringResource(Res.string.proximity_checking_device))
                    is ProximityState.CheckingPrerequisites -> ProximityPrerequisiteContent(
                        capabilities = sessionState.capabilities,
                        hostActionInProgress = state.hostActionInProgress,
                        hostActionForDisplay = hostActionForDisplay,
                        onRetry = onRetry,
                        onContinueWithAvailableConnection = onContinueWithAvailableConnection,
                        onRemediate = { onRemediate(it, hostActions) },
                    )
                    is ProximityState.Preparing ->
                        ProximityProgressContent(stringResource(Res.string.proximity_preparing))
                    is ProximityState.EngagementReady -> Unit // Uses the bounded engagement layout above.
                    is ProximityState.Connecting -> ProximityProgressContent(stringResource(Res.string.proximity_reader_detected))
                    is ProximityState.AwaitingRequest ->
                        ProximityProgressContent(stringResource(Res.string.proximity_awaiting_request))
                    is ProximityState.ReviewRequired, is ProximityState.PreparationRequired -> Unit
                    is ProximityState.AuthorizingHolderKey ->
                        ProximityProgressContent(stringResource(Res.string.proximity_authenticating))
                    is ProximityState.SendingResponse ->
                        ProximityProgressContent(stringResource(Res.string.proximity_send_response))
                    is ProximityState.AwaitingNextRequest ->
                        ProximityProgressContent(stringResource(Res.string.proximity_awaiting_next_request))
                    is ProximityState.Terminating ->
                        ProximityProgressContent(stringResource(Res.string.proximity_terminating))
                    is ProximityState.Completed -> {
                        ProximityTerminalContent(
                        title = if (sessionState.declined) {
                            stringResource(Res.string.proximity_declined_title)
                        } else {
                            stringResource(Res.string.proximity_presentation_complete)
                        },
                        message = if (sessionState.declined) {
                            stringResource(Res.string.proximity_declined_message)
                        } else {
                            stringResource(Res.string.proximity_presentation_complete_message)
                        },
                            details = {
                                sessionState.receipt?.let { ProximitySharingReceiptContent(it.review, it.submission, it.approvalTiming, it.completedAt, credentialDetailsById) }

                            },
                        )
                    }
                    is ProximityState.NoData -> ProximityTerminalContent(
                        title = stringResource(Res.string.proximity_no_data_title),
                        message = stringResource(Res.string.proximity_declined_message),
                    )
                    ProximityState.Cancelled -> ProximityTerminalContent(
                        title = stringResource(Res.string.proximity_cancelled_title),
                        message = stringResource(Res.string.proximity_cancelled_message),
                    )
                    is ProximityState.Failed -> ProximityFailedContent(sessionState.error)
                }
                state.connectedRoute?.let { ProximityConnectionDetails(it) }
            }
        }
    }
}

@Composable
private fun WalletDemoProximityReview(
    state: WalletDemoProximityUiState,
    review: ProximityReview,
    credentialDetailsById: Map<String, CredentialDetails>,
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, ProximityElementReference) -> Unit,
    onContinueAfterResponseChange: (Boolean) -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    headerOwnsClose: Boolean = false,
) {
    SystemBackHandler(enabled = true, onBack = onCancel)
    ReviewScaffold(
        modifier = modifier,
        feedback = state.actionError?.let { error -> { ProximityErrorCard(error) } },
        actions = {
            if (state.preparingApproval) {
                val expired = (state.sessionState as ProximityState.PreparationRequired).plan.isExpired
                WalletActions(
                    primary = WalletAction(stringResource(if (expired) Res.string.proximity_refresh_request else Res.string.proximity_approve_and_prepare),
                        onClick = if (expired) onRestart else onApprove, enabled = expired || state.canApprove,
                        testTag = WalletUiTestTags.ProximityApprove),
                    secondary = if (headerOwnsClose) null else WalletAction(stringResource(Res.string.proximity_cancel), onCancel,
                        testTag = WalletUiTestTags.ProximityCancel),
                )
            } else SharingActionsRow(
                enabled = state.pendingReviewId == null,
                selectionComplete = state.canApprove,
                onSubmit = onApprove,
                onCancel = onCancel,
                onReject = onDecline,
                presentation = ReviewActionPresentation.Proximity,
                showCancelWithReject = !headerOwnsClose,
            )
        },
    ) {
        val reason = when (val current = state.sessionState) {
            is ProximityState.ReviewRequired -> current.reason
            is ProximityState.PreparationRequired -> current.reason
            else -> null
        }
        if (reason == ProximityReviewReason.PreparedSharingChanged) {
            Text(stringResource(Res.string.proximity_prepared_request_changed), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
        if (state.preparingApproval) {
            Text(stringResource(Res.string.proximity_review_before_reconnect), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.proximity_nothing_shared_prepare))
        }
        ProximityReviewContent(
            review = review,
            selections = state.selections,
            credentialDetailsById = credentialDetailsById,
            continueAfterResponse = state.continueAfterResponse,
            onSelectCredential = onSelectCredential,
            onToggleElement = onToggleElement,
            onContinueAfterResponseChange = onContinueAfterResponseChange,
            allowContinuation = !state.preparingApproval,
            enabled = state.pendingReviewId == null,
        )
        (state.sessionState as? ProximityState.PreparationRequired)?.plan?.let { plan ->
            MetadataDisclosure(title = stringResource(Res.string.proximity_reader_certificate), initiallyExpanded = false) {
                Text(plan.readerCertificateSha256, style = MaterialTheme.typography.bodySmall)
            }
        }
        state.connectedRoute?.let { ProximityConnectionDetails(it) }
    }
}

@Composable
private fun ProximityCancelAction(onCancel: () -> Unit) {
    WalletActions(secondary = WalletAction(stringResource(Res.string.proximity_cancel), onCancel,
        testTag = WalletUiTestTags.ProximityCancel))
}
