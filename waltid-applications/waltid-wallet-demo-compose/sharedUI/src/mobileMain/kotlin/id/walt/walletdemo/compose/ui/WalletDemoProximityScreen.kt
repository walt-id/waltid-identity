package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityActionType
import id.walt.wallet2.mobile.ProximityCapabilities
import id.walt.wallet2.mobile.ProximityCredentialOption
import id.walt.wallet2.mobile.ProximityDeviceAuthenticationMethod
import id.walt.wallet2.mobile.ProximityDocumentReview
import id.walt.wallet2.mobile.ProximityElementReference
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.wallet2.mobile.ProximityEngagementMethod
import id.walt.wallet2.mobile.ProximityError
import id.walt.wallet2.mobile.ProximityReaderAuthentication
import id.walt.wallet2.mobile.ProximityReaderAuthenticationSummary
import id.walt.wallet2.mobile.ProximityReaderAuthenticationScope
import id.walt.wallet2.mobile.ProximityReaderAuthenticationValidity
import id.walt.wallet2.mobile.ProximityRecovery
import id.walt.wallet2.mobile.ProximityReaderCertificatePathState
import id.walt.wallet2.mobile.ProximityReaderRevocationState
import id.walt.wallet2.mobile.ProximityReaderTrustState
import id.walt.wallet2.mobile.ProximityRemediationAction
import id.walt.wallet2.mobile.ProximityReview
import id.walt.wallet2.mobile.ProximityRicalState
import id.walt.wallet2.mobile.ProximityState
import id.walt.wallet2.mobile.legalActions
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController
import id.walt.walletdemo.compose.logic.WalletDemoProximityController
import id.walt.walletdemo.compose.logic.WalletDemoProximityDocumentSelection
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.ui.components.QrCodeCanvas
import id.walt.walletdemo.compose.ui.components.encodeProximityQrCode
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.components.ClaimValueRow
import id.walt.walletdemo.compose.ui.components.CredentialCard
import id.walt.walletdemo.compose.ui.components.ExpandableMetadataCard
import id.walt.walletdemo.compose.ui.components.MetadataDetailItem
import id.walt.walletdemo.compose.ui.components.MetadataDetailList
import id.walt.walletdemo.compose.ui.components.MetadataDisclosure
import id.walt.walletdemo.compose.ui.components.ReviewMetadataSection
import id.walt.walletdemo.compose.ui.components.ReviewActionPresentation
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.SharingActionsRow
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Mobile-only host that adds the shared proximity journey to the regular Compose demo. */
@Composable
fun MobileWalletDemoApp(
    controller: WalletDemoController,
    proximityController: WalletDemoProximityController,
    readerTrustSettingsController: DemoReaderTrustSettingsController,
    branding: WalletDemoBranding = WalletDemoBranding(),
) {
    val walletState by controller.state.collectAsState()
    val proximity by proximityController.state.collectAsState()
    val hostActions = rememberProximityHostActions()
    val credentials = (walletState.session as? WalletSessionState.Ready)
        ?.credentials
        .orEmpty()
    val credentialDetailsById = remember(credentials) {
        credentials.associate { credential -> credential.id to credential.toCredentialDetails() }
    }
    val qrVisible = walletState.selectedTab == WalletDemoTab.Present &&
        proximity.qrVisible

    ProximityPlatformSessionEffect(
        active = proximity.active && !proximity.isTerminal,
        qrVisible = qrVisible,
        nfcReviewVisible = proximity.review != null,
        onInterrupted = proximityController::handleLifecycleInterruption,
    )
    LaunchedEffect(walletState.auth, proximity.active) {
        if (proximity.active && walletState.auth !is WalletAuthState.Unlocked) proximityController.dismiss()
    }
    LaunchedEffect(walletState.selectedTab, proximity.active) {
        if (proximity.active && walletState.selectedTab != WalletDemoTab.Present) proximityController.cancel()
    }
    WalletDemoAppHost(
        controller = controller,
        branding = branding,
        onStartProximityPresentation = proximityController::start,
        presentationContent = if (proximity.active) {
            {
                WalletDemoProximityScreen(
                    state = proximity,
                    credentialDetailsById = credentialDetailsById,
                    hostActions = hostActions.executor,
                    hostActionForDisplay = hostActions::displayedAction,
                    onSelectCredential = proximityController::selectCredential,
                    onToggleElement = proximityController::toggleElement,
                    onContinueAfterResponseChange = proximityController::setContinueAfterResponse,
                    onApprove = { proximityController.approve() },
                    onDecline = proximityController::decline,
                    onRetry = proximityController::retryPrerequisites,
                    onRemediate = proximityController::remediate,
                    onCancel = proximityController::cancel,
                    onDismiss = proximityController::dismiss,
                    onRestart = proximityController::restart,
                    onShowEngagement = proximityController::showEngagement,
                    onContinueWithAvailableConnection = proximityController::continueWithAvailableConnection,
                )
            }
        } else null,
        sharingSettingsContent = {
            DemoReaderTrustSettings(
                controller = readerTrustSettingsController,
            )
        },
    )
}

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
    onShowEngagement: (MobileWalletProximityEngagementMethod) -> Unit = {},
    onContinueWithAvailableConnection: () -> Unit = {},
) {
    val sessionState = state.sessionState
    val terminal = state.isTerminal
    val canCancel = !terminal && (
        sessionState == null || ProximityActionType.Cancel in sessionState.legalActions
    )
    val screenTitle = stringResource(Res.string.proximity_in_person_title)
    SystemBackHandler(
        enabled = sessionState !is ProximityState.ReviewRequired && (terminal || canCancel),
    ) {
        if (terminal) onDismiss() else onCancel()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.ProximityScreen)
            .semantics { paneTitle = screenTitle },
    ) {
        if (sessionState is ProximityState.ReviewRequired) {
            WalletDemoProximityReview(
                state = state,
                review = sessionState.review,
                credentialDetailsById = credentialDetailsById,
                onSelectCredential = onSelectCredential,
                onToggleElement = onToggleElement,
                onContinueAfterResponseChange = onContinueAfterResponseChange,
                onApprove = onApprove,
                onDecline = onDecline,
                onCancel = onCancel,
            )
        } else {
            ReviewScaffold(
                actions = if (canCancel) {
                    {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.ProximityCancel),
                        ) {
                            Text(stringResource(Res.string.proximity_cancel))
                        }
                    }
                } else null,
            ) {
                state.actionError?.takeUnless { it == (sessionState as? MobileWalletProximityState.Failed)?.error }?.let { ProximityErrorCard(it) }
                when (sessionState) {
                    null -> ProgressContent(stringResource(Res.string.proximity_checking_device))
                    is ProximityState.CheckingPrerequisites -> PrerequisiteContent(
                        capabilities = sessionState.capabilities,
                        hostActionInProgress = state.hostActionInProgress,
                        hostActionForDisplay = hostActionForDisplay,
                        onRetry = onRetry,
                        onContinueWithAvailableConnection = onContinueWithAvailableConnection,
                        onRemediate = { onRemediate(it, hostActions) },
                    )
                    is ProximityState.Preparing ->
                        ProgressContent(stringResource(Res.string.proximity_preparing))
                    is MobileWalletProximityState.EngagementReady -> EngagementContent(
                        state = state,
                        onShowEngagement = onShowEngagement,
                    )
                    is MobileWalletProximityState.Connecting -> ProgressContent(stringResource(Res.string.proximity_reader_detected))
                    is MobileWalletProximityState.AwaitingRequest ->
                        ProgressContent(stringResource(Res.string.proximity_awaiting_request))
                    is ProximityState.ReviewRequired -> Unit
                    is ProximityState.AuthorizingHolderKey ->
                        ProgressContent(stringResource(Res.string.proximity_authenticating))
                    is ProximityState.SendingResponse ->
                        ProgressContent(stringResource(Res.string.proximity_send_response))
                    is ProximityState.AwaitingNextRequest ->
                        ProgressContent(stringResource(Res.string.proximity_awaiting_next_request))
                    is ProximityState.Terminating ->
                        ProgressContent(stringResource(Res.string.proximity_terminating))
                    is ProximityState.Completed -> TerminalContent(
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
                        onDismiss = onDismiss,
                    )
                    is ProximityState.NoData -> TerminalContent(
                        title = stringResource(Res.string.proximity_no_data_title),
                        message = stringResource(Res.string.proximity_declined_message),
                        onDismiss = onDismiss,
                    )
                    ProximityState.Cancelled -> TerminalContent(
                        title = stringResource(Res.string.proximity_cancelled_title),
                        message = stringResource(Res.string.proximity_cancelled_message),
                        onDismiss = onDismiss,
                    )
                    is ProximityState.Failed -> FailedContent(
                        error = sessionState.error,
                        onRemediate = { onRemediate(it, hostActions) },
                        hostActionForDisplay = hostActionForDisplay,
                        actionInProgress = state.hostActionInProgress != null,
                        onDismiss = onDismiss,
                        onRetry = if (sessionState.error.recovery == ProximityRecovery.StartNewSession) onRestart else null,
                    )
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
    modifier: Modifier = Modifier,
) {
    SystemBackHandler(enabled = true, onBack = onCancel)
    ReviewScaffold(
        modifier = modifier,
        actions = {
            SharingActionsRow(
                enabled = true,
                selectionComplete = state.canApprove,
                onSubmit = onApprove,
                onCancel = onCancel,
                onReject = onDecline,
                presentation = ReviewActionPresentation.Proximity,
            )
        },
    ) {
        state.actionError?.let { ProximityErrorCard(it) }
        ReviewContent(
            review = review,
            selections = state.selections,
            credentialDetailsById = credentialDetailsById,
            continueAfterResponse = state.continueAfterResponse,
            onSelectCredential = onSelectCredential,
            onToggleElement = onToggleElement,
            onContinueAfterResponseChange = onContinueAfterResponseChange,
        )
        state.connectedRoute?.let { ProximityConnectionDetails(it) }
    }
}

@Composable
private fun PrerequisiteContent(
    capabilities: ProximityCapabilities,
    hostActionInProgress: ProximityRemediationAction?,
    hostActionForDisplay: (ProximityRemediationAction) -> ProximityRemediationAction,
    onRetry: () -> Unit,
    onContinueWithAvailableConnection: () -> Unit,
    onRemediate: (MobileWalletProximityRemediationAction) -> Unit,
) {
    val action = capabilities.remediationActions.firstOrNull { it != MobileWalletProximityRemediationAction.UseSupportedDevice }
    val message = listOf(capabilities.nfcEngagement, capabilities.qrEngagement, capabilities.bluetoothLowEnergy,
        capabilities.nfcRetrieval, capabilities.nfcV2Retrieval, capabilities.wifiAwareRetrieval)
        .firstOrNull { it.selected && action in it.remediationActions }?.unavailable?.message
        ?: capabilities.selectedUnavailableMessage ?: stringResource(Res.string.proximity_generic_unavailable)
    ReviewMetadataSection(title = action?.let(hostActionForDisplay)?.label() ?: stringResource(Res.string.proximity_action_needed)) {
        Text(message)
        if (action != null) {
            Button(onClick = { onRemediate(action) }, enabled = hostActionInProgress == null, modifier = Modifier.fillMaxWidth()) {
                if (hostActionInProgress != null) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(hostActionForDisplay(action).label())
            }
        } else {
            Button(onClick = onRetry, enabled = hostActionInProgress == null,
                modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.ProximityRetry)) {
                Text(stringResource(Res.string.proximity_check_again))
            }
        }
        if (capabilities.mayStart) {
            TextButton(onClick = onContinueWithAvailableConnection, enabled = hostActionInProgress == null) {
                Text(stringResource(Res.string.proximity_continue_available))
            }
        }
    }
}

private val MobileWalletProximityCapabilities.selectedUnavailableMessage: String?
    get() = listOf(
        nfcEngagement,
        bluetoothLowEnergy,
        nfcRetrieval,
        nfcV2Retrieval,
        qrEngagement,
        wifiAwareRetrieval,
    ).firstNotNullOfOrNull { capability -> capability.unavailable?.message.takeIf { capability.selected } }

@Composable
private fun EngagementContent(
    state: WalletDemoProximityUiState,
    onShowEngagement: (MobileWalletProximityEngagementMethod) -> Unit,
) {
    val method = state.displayedEngagement
    val choices = state.engagementChoices
    if (method == null) {
        Text(stringResource(Res.string.proximity_share_in_person), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.proximity_choose_connection))
        choices.forEach { choice -> ProximityEngagementChoice(choice) { onShowEngagement(choice) } }
        return
    }
    val qr = (state.sessionState as? MobileWalletProximityState.EngagementReady)?.engagements
        ?.filterIsInstance<MobileWalletProximityEngagement.Qr>()?.singleOrNull()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(if (method == MobileWalletProximityEngagementMethod.Qr)
            Res.string.proximity_show_qr else Res.string.proximity_reader_hold_title),
            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(if (method == MobileWalletProximityEngagementMethod.Qr)
            Res.string.proximity_qr_instructions else Res.string.proximity_tap_instructions), textAlign = TextAlign.Center)
        if (method == MobileWalletProximityEngagementMethod.Qr && qr != null) {
            val qrCode = remember(qr.payload) { runCatching { encodeProximityQrCode(qr.payload) }.getOrNull() }
            if (qrCode != null) {
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    QrCodeCanvas(
                        qrCode = qrCode,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .semantics { contentDescription = "Device engagement QR code" }
                            .testTag(WalletUiTestTags.ProximityQr),
                    )
                }
            } else {
                Text(stringResource(Res.string.proximity_qr_render_failed), color = MaterialTheme.colorScheme.error)
            }
        }
        choices.filter { it != method }.forEach { other ->
            TextButton(onClick = { onShowEngagement(other) }) {
                Text(stringResource(if (other == MobileWalletProximityEngagementMethod.Qr)
                    Res.string.proximity_show_qr_instead else Res.string.proximity_tap_instead))
            }
        }
    }
}

@Composable
private fun ReviewContent(
    review: ProximityReview,
    selections: List<WalletDemoProximityDocumentSelection>,
    credentialDetailsById: Map<String, CredentialDetails>,
    continueAfterResponse: Boolean,
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, ProximityElementReference) -> Unit,
    onContinueAfterResponseChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.ProximityReview),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReaderMetadataCard(review, credentialDetailsById)
        review.useCases.forEach { useCase ->
            ReviewMetadataSection(stringResource(Res.string.proximity_reader_purpose)) {
                Text(
                    stringResource(
                        Res.string.proximity_use_case,
                        useCase.index + 1,
                        if (useCase.mandatory) stringResource(Res.string.proximity_mandatory_suffix) else "",
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                if (useCase.purposeHints.isEmpty()) {
                    Text(stringResource(Res.string.proximity_no_purpose))
                } else {
                    useCase.purposeHints.forEach { hint ->
                        Text(stringResource(Res.string.proximity_purpose_hint, hint.type, hint.code))
                    }
                }
            }
        }
        review.applicationAuthorizations.forEach { authorization ->
            ReviewMetadataSection(authorization.displayTitle) {
                Text(
                    stringResource(Res.string.proximity_validated_application_request),
                    style = MaterialTheme.typography.labelLarge,
                )
                MetadataDetailList(
                    authorization.details.map { detail -> MetadataDetailItem(detail.label, detail.value) }
                )
            }
        }
        review.documents.forEach { document ->
            DocumentReviewContent(
                document = document,
                selection = selections.singleOrNull { it.requestIndex == document.requestIndex },
                credentialDetailsById = credentialDetailsById,
                onSelectCredential = onSelectCredential,
                onToggleElement = onToggleElement,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = continueAfterResponse,
                onCheckedChange = onContinueAfterResponseChange,
                modifier = Modifier.testTag(WalletUiTestTags.ProximityContinueAfterResponse),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.proximity_continue_after_response))
                Text(
                    stringResource(Res.string.proximity_continue_after_response_description),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ReaderMetadataCard(
    review: ProximityReview,
    credentialDetailsById: Map<String, CredentialDetails>,
) {
    val suppliedAuthentications = review.readerAuthentication.filterNot {
        it.validity == ProximityReaderAuthenticationValidity.Absent
    }
    if (suppliedAuthentications.isEmpty()) {
        ReviewMetadataSection(
            title = "Verifier",
            modifier = Modifier.testTag(WalletUiTestTags.ProximityReaderSection),
        ) {
            Text(
                stringResource(Res.string.proximity_reader_identity_not_provided),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.proximity_reader_not_authenticated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val displayNames = suppliedAuthentications.mapNotNull { authentication ->
        authentication.displayName?.trim()?.takeIf(String::isNotEmpty)
    }.distinct()
    val displayName = when (displayNames.size) {
        0 -> stringResource(Res.string.proximity_reader_identity_unavailable)
        1 -> displayNames.single()
        else -> stringResource(Res.string.proximity_multiple_reader_identities)
    }
    val supportingText = when (review.readerAuthenticationSummary) {
        ProximityReaderAuthenticationSummary.Absent -> ProximityReaderAuthenticationValidity.Absent.displayName()
        ProximityReaderAuthenticationSummary.Malformed -> ProximityReaderAuthenticationValidity.Malformed.displayName()
        ProximityReaderAuthenticationSummary.Invalid -> ProximityReaderAuthenticationValidity.Invalid.displayName()
        ProximityReaderAuthenticationSummary.Revoked -> ProximityReaderTrustState.Revoked.displayName()
        ProximityReaderAuthenticationSummary.Partial -> stringResource(Res.string.proximity_reader_authentication_partial)
        ProximityReaderAuthenticationSummary.ValidButUntrusted -> ProximityReaderTrustState.ValidButUntrusted.displayName()
        ProximityReaderAuthenticationSummary.Trusted -> ProximityReaderTrustState.Trusted.displayName()
    }

    ExpandableMetadataCard(
        title = "Verifier",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = Modifier.testTag(WalletUiTestTags.ProximityReaderSection),
        toggleTestTag = WalletUiTestTags.ProximityReaderDetailsToggle,
        summary = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        details = {
            Column(
                modifier = Modifier.testTag(WalletUiTestTags.ProximityReaderDetails),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                review.readerAuthentication.forEachIndexed { index, readerAuthentication ->
                    if (index > 0) HorizontalDivider()
                    ReaderAuthenticationContent(
                        readerAuthentication,
                        review.documents,
                        credentialDetailsById,
                    )
                }
            }
        },
    )
}

@Composable
private fun ReaderAuthenticationContent(
    authentication: ProximityReaderAuthentication,
    documents: List<ProximityDocumentReview>,
    credentialDetailsById: Map<String, CredentialDetails>,
) {
    val trusted = authentication.trust == ProximityReaderTrustState.Trusted
    Text(
        authentication.displayName ?: stringResource(Res.string.proximity_reader_identity_unavailable),
        fontWeight = FontWeight.SemiBold,
    )
    MetadataDetailList(
        listOf(
            MetadataDetailItem(
                stringResource(Res.string.proximity_applies_to),
                when (val scope = authentication.scope) {
                    ProximityReaderAuthenticationScope.WholeRequest ->
                        stringResource(Res.string.proximity_whole_request)
                    is ProximityReaderAuthenticationScope.Document -> {
                        val document = documents.singleOrNull {
                            it.requestIndex == scope.index
                        }
                        document?.let {
                            val displayName = it.credentialOptions.firstNotNullOfOrNull { option ->
                                credentialDetailsById[option.credentialId]?.toCardDisplayData()?.title
                            } ?: it.docType
                            stringResource(Res.string.proximity_document_scope, displayName)
                        } ?: stringResource(
                            Res.string.proximity_document_request,
                            scope.index + 1,
                        )
                    }
                },
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_signature),
                authentication.validity.displayName(),
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_certificate_path),
                authentication.certificatePath.displayName(),
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_revocation),
                authentication.revocation.displayName(),
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_rical_evidence),
                authentication.rical.displayName(),
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_trust),
                authentication.trust.displayName(),
            ),
        )
    )
    authentication.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (!trusted && authentication.validity == ProximityReaderAuthenticationValidity.Valid) {
        Text(
            stringResource(Res.string.proximity_reader_trust_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DocumentReviewContent(
    document: ProximityDocumentReview,
    selection: WalletDemoProximityDocumentSelection?,
    credentialDetailsById: Map<String, CredentialDetails>,
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, ProximityElementReference) -> Unit,
) {
    ReviewMetadataSection(stringResource(Res.string.proximity_credential_to_share)) {
        if (document.credentialOptions.size > 1) {
            Text(stringResource(Res.string.proximity_choose_credential), style = MaterialTheme.typography.labelLarge)
        }
        document.credentialOptions.forEach { credential ->
            CredentialOption(
                requestIndex = document.requestIndex,
                credential = credential,
                details = credentialDetailsById[credential.credentialId],
                showSelectionControl = document.credentialOptions.size > 1,
                selected = selection?.credentialId == credential.credentialId,
                onSelect = onSelectCredential,
            )
        }
        val selectedCredential = document.credentialOptions.singleOrNull {
            it.credentialId == selection?.credentialId
        }
        selectedCredential?.let { credential ->
            val details = credentialDetailsById[credential.credentialId]
            HorizontalDivider()
            Text(stringResource(Res.string.proximity_data_to_share), style = MaterialTheme.typography.labelLarge)
            credential.requestedElements.forEach { element ->
                val reference = ProximityElementReference(
                    namespace = element.namespace,
                    elementIdentifier = element.elementIdentifier,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = reference in (selection?.disclosedElements ?: emptySet()),
                        onCheckedChange = { onToggleElement(document.requestIndex, reference) },
                        modifier = Modifier.testTag(
                            WalletUiTestTags.proximityElement(
                                document.requestIndex,
                                element.namespace,
                                element.elementIdentifier,
                            )
                        ),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val claims = details?.mdocClaims(element.namespace, element.elementIdentifier).orEmpty()
                        if (claims.isNotEmpty()) {
                            claims.forEach { claim -> ClaimValueRow(claim) }
                        } else {
                            Text(
                                humanizedElementIdentifier(element.elementIdentifier),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(Res.string.proximity_value_preview_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (element.intentToRetain) {
                            Text(
                                stringResource(Res.string.proximity_reader_retention),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            MetadataDisclosure(
                title = stringResource(Res.string.proximity_technical_details),
                initiallyExpanded = false,
            ) {
                MetadataDetailList(
                    buildList {
                        add(
                            MetadataDetailItem(
                                stringResource(Res.string.proximity_document_type),
                                document.docType,
                            )
                        )
                        add(
                            MetadataDetailItem(
                                stringResource(Res.string.proximity_device_authentication),
                                credential.deviceAuthentication.displayName(),
                            )
                        )
                        credential.requestedElements.forEach { element ->
                            add(
                                MetadataDetailItem(
                                    stringResource(Res.string.proximity_requested_element),
                                    "${element.namespace} / ${element.elementIdentifier}",
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CredentialOption(
    requestIndex: Int,
    credential: ProximityCredentialOption,
    details: CredentialDetails?,
    showSelectionControl: Boolean,
    selected: Boolean,
    onSelect: (Int, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.proximityCredential(requestIndex, credential.credentialId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSelectionControl) {
            RadioButton(
                selected = selected,
                onClick = { onSelect(requestIndex, credential.credentialId) },
            )
        }
        if (details != null) {
            CredentialCard(
                details = details,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(requestIndex, credential.credentialId) },
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(credential.label ?: stringResource(Res.string.proximity_generic_credential))
                credential.issuer?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    stringResource(Res.string.proximity_valid_until, credential.validUntil.toString()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun CredentialDetails.mdocClaims(namespace: String, elementIdentifier: String): List<ClaimItem> =
    groups.asSequence()
        .flatMap { group -> group.items.asSequence() }
        .filter { claim ->
            claim.pathComponents.getOrNull(0) == namespace &&
                claim.pathComponents.getOrNull(1) == elementIdentifier
        }
        .toList()

private fun humanizedElementIdentifier(identifier: String): String =
    identifier
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replaceFirstChar { character -> character.uppercase() }

@Composable
private fun ProgressContent(message: String) {
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
private fun TerminalContent(title: String, message: String, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message)
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.ProximityDone),
        ) { Text(stringResource(Res.string.proximity_done)) }
    }
}

@Composable
private fun FailedContent(
    error: MobileWalletProximityError,
    onRemediate: (MobileWalletProximityRemediationAction) -> Unit,
    hostActionForDisplay: (MobileWalletProximityRemediationAction) -> MobileWalletProximityRemediationAction,
    actionInProgress: Boolean,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    ReviewMetadataSection(stringResource(Res.string.proximity_failed_title)) {
        Text(error.message, modifier = Modifier.testTag(WalletUiTestTags.ProximityError))
        error.remediationActions.firstOrNull { it != MobileWalletProximityRemediationAction.Retry && it != MobileWalletProximityRemediationAction.UseSupportedDevice }?.let { action ->
            OutlinedButton(onClick = { onRemediate(action) }, enabled = !actionInProgress, modifier = Modifier.fillMaxWidth()) {
                Text(hostActionForDisplay(action).label())
            }
        }
        if (onRetry != null && error.remediationActions.none { it != MobileWalletProximityRemediationAction.Retry && it != MobileWalletProximityRemediationAction.UseSupportedDevice }) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.ProximityRetry),
            ) { Text(stringResource(Res.string.proximity_try_again)) }
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.ProximityDone),
        ) { Text(stringResource(Res.string.proximity_done)) }
    }
}

@Composable
private fun ProximityErrorCard(error: ProximityError) {
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

private fun ProximityState?.engagements(): List<ProximityEngagement> = when (this) {
    is ProximityState.EngagementReady -> engagements
    is ProximityState.Connecting -> engagements
    else -> emptyList()
}

@Composable
internal fun MobileWalletProximityRemediationAction.label(): String = stringResource(
    when (this) {
        ProximityRemediationAction.RequestBluetoothPermission -> Res.string.proximity_allow_bluetooth
        ProximityRemediationAction.OpenApplicationSettings -> Res.string.proximity_open_app_settings
        ProximityRemediationAction.EnableBluetooth -> Res.string.proximity_enable_bluetooth
        ProximityRemediationAction.EnableNfc -> Res.string.proximity_enable_nfc
        ProximityRemediationAction.UseSupportedDevice -> Res.string.proximity_use_supported_device
        ProximityRemediationAction.Retry -> Res.string.proximity_try_again
    }
)

@Composable
private fun ProximityReaderAuthenticationValidity.displayName(): String = stringResource(
    when (this) {
        ProximityReaderAuthenticationValidity.Absent -> Res.string.proximity_auth_absent
        ProximityReaderAuthenticationValidity.Malformed -> Res.string.proximity_auth_malformed
        ProximityReaderAuthenticationValidity.Invalid -> Res.string.proximity_auth_invalid
        ProximityReaderAuthenticationValidity.Valid -> Res.string.proximity_auth_valid
    }
)

@Composable
private fun ProximityReaderTrustState.displayName(): String = stringResource(
    when (this) {
        ProximityReaderTrustState.NotEvaluated -> Res.string.proximity_trust_not_evaluated
        ProximityReaderTrustState.ValidButUntrusted -> Res.string.proximity_trust_untrusted
        ProximityReaderTrustState.Revoked -> Res.string.proximity_trust_revoked
        ProximityReaderTrustState.Trusted -> Res.string.proximity_trust_trusted
    }
)

@Composable
private fun ProximityReaderCertificatePathState.displayName(): String = stringResource(
    when (this) {
        ProximityReaderCertificatePathState.NotEvaluated -> Res.string.proximity_not_evaluated
        ProximityReaderCertificatePathState.UnknownAuthority -> Res.string.proximity_unknown_authority
        ProximityReaderCertificatePathState.Invalid -> Res.string.proximity_auth_invalid
        ProximityReaderCertificatePathState.Valid -> Res.string.proximity_auth_valid
    }
)

@Composable
private fun ProximityReaderRevocationState.displayName(): String = stringResource(
    when (this) {
        ProximityReaderRevocationState.NotChecked -> Res.string.proximity_not_checked
        ProximityReaderRevocationState.Good -> Res.string.proximity_revocation_good
        ProximityReaderRevocationState.Revoked -> Res.string.proximity_trust_revoked
        ProximityReaderRevocationState.Indeterminate -> Res.string.proximity_indeterminate
    }
)

@Composable
private fun ProximityRicalState.displayName(): String = stringResource(
    when (this) {
        ProximityRicalState.NotEvaluated -> Res.string.proximity_not_evaluated
        ProximityRicalState.Unavailable -> Res.string.proximity_unavailable
        ProximityRicalState.Invalid -> Res.string.proximity_auth_invalid
        ProximityRicalState.NoMatchingAuthority -> Res.string.proximity_no_matching_authority
        ProximityRicalState.Matched -> Res.string.proximity_matched_authority
    }
)

@Composable
private fun ProximityDeviceAuthenticationMethod.displayName(): String = stringResource(
    when (this) {
        ProximityDeviceAuthenticationMethod.Signature -> Res.string.proximity_auth_signature
        ProximityDeviceAuthenticationMethod.Mac -> Res.string.proximity_auth_mac
    }
)
