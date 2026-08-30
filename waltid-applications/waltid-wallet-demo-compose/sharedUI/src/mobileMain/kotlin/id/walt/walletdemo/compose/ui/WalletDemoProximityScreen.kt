package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.MobileWalletProximityActionType
import id.walt.wallet2.mobile.MobileWalletProximityCapabilities
import id.walt.wallet2.mobile.MobileWalletProximityCredentialOption
import id.walt.wallet2.mobile.MobileWalletProximityDeviceAuthenticationMethod
import id.walt.wallet2.mobile.MobileWalletProximityDocumentReview
import id.walt.wallet2.mobile.MobileWalletProximityElementReference
import id.walt.wallet2.mobile.MobileWalletProximityEngagement
import id.walt.wallet2.mobile.MobileWalletProximityError
import id.walt.wallet2.mobile.MobileWalletProximityReaderAuthentication
import id.walt.wallet2.mobile.MobileWalletProximityReaderAuthenticationScope
import id.walt.wallet2.mobile.MobileWalletProximityReaderAuthenticationValidity
import id.walt.wallet2.mobile.MobileWalletProximityReaderCertificatePathState
import id.walt.wallet2.mobile.MobileWalletProximityReaderRevocationState
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustState
import id.walt.wallet2.mobile.MobileWalletProximityRemediationAction
import id.walt.wallet2.mobile.MobileWalletProximityReview
import id.walt.wallet2.mobile.MobileWalletProximityRicalState
import id.walt.wallet2.mobile.MobileWalletProximityState
import id.walt.wallet2.mobile.legalActions
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoProximityController
import id.walt.walletdemo.compose.logic.WalletDemoProximityDocumentSelection
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.logic.WalletAuthState
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
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource

/** Mobile-only host that adds the shared proximity journey to the regular Compose demo. */
@Composable
fun MobileWalletDemoApp(
    controller: WalletDemoController,
    proximityController: WalletDemoProximityController,
    branding: WalletDemoBranding = WalletDemoBranding(),
) {
    val walletState by controller.state.collectAsState()
    val proximity by proximityController.state.collectAsState()
    val hostActions = rememberProximityHostActionExecutor()
    val credentials = (walletState.session as? WalletSessionState.Ready)
        ?.credentials
        .orEmpty()
    val credentialDetailsById = remember(credentials) {
        credentials.associate { credential -> credential.id to credential.toCredentialDetails() }
    }
    val qrVisible = walletState.selectedTab == WalletDemoTab.Present &&
        proximity.sessionState.engagements().any { it is MobileWalletProximityEngagement.Qr }

    ProximityPlatformSessionEffect(
        active = proximity.active && !proximity.isTerminal,
        qrVisible = qrVisible,
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
                    hostActions = hostActions,
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
                )
            }
        } else null,
    )
}

@Composable
internal fun WalletDemoProximityScreen(
    state: WalletDemoProximityUiState,
    credentialDetailsById: Map<String, CredentialDetails>,
    hostActions: WalletDemoProximityHostActionExecutor,
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, MobileWalletProximityElementReference) -> Unit,
    onContinueAfterResponseChange: (Boolean) -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onRetry: () -> Unit,
    onRemediate: (MobileWalletProximityRemediationAction, WalletDemoProximityHostActionExecutor) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
) {
    val sessionState = state.sessionState
    val terminal = state.isTerminal
    val canCancel = !terminal && (
        sessionState == null || MobileWalletProximityActionType.Cancel in sessionState.legalActions
    )
    val screenTitle = stringResource(Res.string.proximity_in_person_title)
    SystemBackHandler(
        enabled = sessionState !is MobileWalletProximityState.ReviewRequired && (terminal || canCancel),
    ) {
        if (terminal) onDismiss() else onCancel()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.ProximityScreen)
            .semantics { paneTitle = screenTitle },
    ) {
        if (sessionState is MobileWalletProximityState.ReviewRequired) {
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
                state.actionError?.let { ProximityErrorCard(it) }
                when (sessionState) {
                    null -> ProgressContent(stringResource(Res.string.proximity_checking_device))
                    is MobileWalletProximityState.CheckingPrerequisites -> PrerequisiteContent(
                        capabilities = sessionState.capabilities,
                        hostActionInProgress = state.hostActionInProgress,
                        onRetry = onRetry,
                        onRemediate = { onRemediate(it, hostActions) },
                    )
                    is MobileWalletProximityState.Preparing ->
                        ProgressContent(stringResource(Res.string.proximity_preparing))
                    is MobileWalletProximityState.EngagementReady -> EngagementContent(
                        engagements = sessionState.engagements,
                        connecting = false,
                    )
                    is MobileWalletProximityState.Connecting -> EngagementContent(
                        engagements = sessionState.engagements,
                        connecting = true,
                    )
                    is MobileWalletProximityState.AwaitingRequest ->
                        ProgressContent(stringResource(Res.string.proximity_awaiting_request))
                    is MobileWalletProximityState.ReviewRequired -> Unit
                    is MobileWalletProximityState.AuthorizingHolderKey ->
                        ProgressContent(stringResource(Res.string.proximity_authenticating))
                    is MobileWalletProximityState.SendingResponse ->
                        ProgressContent(stringResource(Res.string.proximity_send_response))
                    is MobileWalletProximityState.AwaitingNextRequest ->
                        ProgressContent(stringResource(Res.string.proximity_awaiting_next_request))
                    is MobileWalletProximityState.Terminating ->
                        ProgressContent(stringResource(Res.string.proximity_terminating))
                    is MobileWalletProximityState.Completed -> TerminalContent(
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
                    MobileWalletProximityState.Cancelled -> TerminalContent(
                        title = stringResource(Res.string.proximity_cancelled_title),
                        message = stringResource(Res.string.proximity_cancelled_message),
                        onDismiss = onDismiss,
                    )
                    is MobileWalletProximityState.Failed -> FailedContent(
                        error = sessionState.error,
                        onDismiss = onDismiss,
                        onRetry = if (sessionState.error.recoverable) onRestart else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletDemoProximityReview(
    state: WalletDemoProximityUiState,
    review: MobileWalletProximityReview,
    credentialDetailsById: Map<String, CredentialDetails>,
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, MobileWalletProximityElementReference) -> Unit,
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
    }
}

@Composable
private fun PrerequisiteContent(
    capabilities: MobileWalletProximityCapabilities,
    hostActionInProgress: MobileWalletProximityRemediationAction?,
    onRetry: () -> Unit,
    onRemediate: (MobileWalletProximityRemediationAction) -> Unit,
) {
    ReviewMetadataSection(
        title = stringResource(
            if (capabilities.mayStart) Res.string.proximity_device_ready else Res.string.proximity_action_needed
        )
    ) {
        Text(
            if (capabilities.mayStart) {
                stringResource(Res.string.proximity_ready_message)
            } else {
                capabilities.bluetoothLowEnergy.unavailable?.message
                    ?: capabilities.qrEngagement.unavailable?.message
                    ?: stringResource(Res.string.proximity_generic_unavailable)
            }
        )
        capabilities.remediationActions.forEach { action ->
            OutlinedButton(
                onClick = { onRemediate(action) },
                enabled = hostActionInProgress == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (hostActionInProgress == action) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(action.label())
            }
        }
        Button(
            onClick = onRetry,
            enabled = hostActionInProgress == null,
            modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.ProximityRetry),
        ) { Text(stringResource(Res.string.proximity_check_again)) }
    }
}

@Composable
private fun EngagementContent(
    engagements: List<MobileWalletProximityEngagement>,
    connecting: Boolean,
) {
    val qr = engagements.filterIsInstance<MobileWalletProximityEngagement.Qr>().singleOrNull()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (connecting) {
                stringResource(Res.string.proximity_reader_detected)
            } else {
                stringResource(Res.string.proximity_reader_scan_title)
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            if (connecting) {
                stringResource(Res.string.proximity_connecting_guidance)
            } else {
                stringResource(Res.string.proximity_reader_scan_guidance)
            },
            textAlign = TextAlign.Center,
        )
        qr?.let { engagement ->
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
            ) {
                Image(
                    painter = rememberQrCodePainter(engagement.payload),
                    contentDescription = stringResource(Res.string.proximity_qr_accessibility),
                    modifier = Modifier
                        .padding(20.dp)
                        .size(280.dp)
                        .testTag(WalletUiTestTags.ProximityQr),
                )
            }
        }
        if (connecting) CircularProgressIndicator()
    }
}

@Composable
private fun ReviewContent(
    review: MobileWalletProximityReview,
    selections: List<WalletDemoProximityDocumentSelection>,
    credentialDetailsById: Map<String, CredentialDetails>,
    continueAfterResponse: Boolean,
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, MobileWalletProximityElementReference) -> Unit,
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
    review: MobileWalletProximityReview,
    credentialDetailsById: Map<String, CredentialDetails>,
) {
    val suppliedAuthentications = review.readerAuthentication.filterNot {
        it.validity == MobileWalletProximityReaderAuthenticationValidity.Absent
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
    val mostSevereAuthentication = review.readerAuthentication.maxByOrNull { it.summarySeverity() }
    val hasMissingAuthentication = review.readerAuthentication.any {
        it.validity == MobileWalletProximityReaderAuthenticationValidity.Absent
    }
    val displayNames = suppliedAuthentications.mapNotNull { authentication ->
        authentication.displayName?.trim()?.takeIf(String::isNotEmpty)
    }.distinct()
    val displayName = when (displayNames.size) {
        0 -> stringResource(Res.string.proximity_reader_identity_unavailable)
        1 -> displayNames.single()
        else -> stringResource(Res.string.proximity_multiple_reader_identities)
    }
    val supportingText = mostSevereAuthentication?.let { authentication ->
        when {
            authentication.validity == MobileWalletProximityReaderAuthenticationValidity.Malformed ||
                authentication.validity == MobileWalletProximityReaderAuthenticationValidity.Invalid ->
                authentication.validity.displayName()
            authentication.trust == MobileWalletProximityReaderTrustState.Revoked ->
                authentication.trust.displayName()
            hasMissingAuthentication -> stringResource(Res.string.proximity_reader_authentication_partial)
            authentication.validity != MobileWalletProximityReaderAuthenticationValidity.Valid ->
                authentication.validity.displayName()
            else -> authentication.trust.displayName()
        }
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
                supportingText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

private fun MobileWalletProximityReaderAuthentication.summarySeverity(): Int = when {
    validity == MobileWalletProximityReaderAuthenticationValidity.Malformed -> 7
    validity == MobileWalletProximityReaderAuthenticationValidity.Invalid -> 6
    trust == MobileWalletProximityReaderTrustState.Revoked -> 5
    validity == MobileWalletProximityReaderAuthenticationValidity.Absent -> 4
    trust == MobileWalletProximityReaderTrustState.ValidButUntrusted -> 3
    trust == MobileWalletProximityReaderTrustState.NotEvaluated -> 2
    else -> 1
}

@Composable
private fun ReaderAuthenticationContent(
    authentication: MobileWalletProximityReaderAuthentication,
    documents: List<MobileWalletProximityDocumentReview>,
    credentialDetailsById: Map<String, CredentialDetails>,
) {
    val trusted = authentication.trust == MobileWalletProximityReaderTrustState.Trusted
    Text(
        authentication.displayName ?: stringResource(Res.string.proximity_reader_identity_unavailable),
        fontWeight = FontWeight.SemiBold,
    )
    MetadataDetailList(
        listOf(
            MetadataDetailItem(
                stringResource(Res.string.proximity_applies_to),
                when (authentication.scope) {
                    MobileWalletProximityReaderAuthenticationScope.WholeRequest ->
                        stringResource(Res.string.proximity_whole_request)
                    MobileWalletProximityReaderAuthenticationScope.Document -> {
                        val document = documents.singleOrNull {
                            it.requestIndex == authentication.documentRequestIndex
                        }
                        document?.let {
                            val displayName = it.credentialOptions.firstNotNullOfOrNull { option ->
                                credentialDetailsById[option.credentialId]?.toCardDisplayData()?.title
                            } ?: it.docType
                            stringResource(Res.string.proximity_document_scope, displayName)
                        } ?: stringResource(
                            Res.string.proximity_document_request,
                            authentication.documentRequestIndex?.plus(1) ?: 0,
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
    if (!trusted && authentication.validity == MobileWalletProximityReaderAuthenticationValidity.Valid) {
        Text(
            stringResource(Res.string.proximity_reader_trust_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DocumentReviewContent(
    document: MobileWalletProximityDocumentReview,
    selection: WalletDemoProximityDocumentSelection?,
    credentialDetailsById: Map<String, CredentialDetails>,
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, MobileWalletProximityElementReference) -> Unit,
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
                val reference = MobileWalletProximityElementReference(
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
    credential: MobileWalletProximityCredentialOption,
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
    ReviewMetadataSection(title) {
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
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    ReviewMetadataSection(stringResource(Res.string.proximity_failed_title)) {
        Text(error.message, modifier = Modifier.testTag(WalletUiTestTags.ProximityError))
        if (onRetry != null) {
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
private fun ProximityErrorCard(error: MobileWalletProximityError) {
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

private fun MobileWalletProximityState?.engagements(): List<MobileWalletProximityEngagement> = when (this) {
    is MobileWalletProximityState.EngagementReady -> engagements
    is MobileWalletProximityState.Connecting -> engagements
    else -> emptyList()
}

@Composable
private fun MobileWalletProximityRemediationAction.label(): String = stringResource(
    when (this) {
        MobileWalletProximityRemediationAction.RequestBluetoothPermission -> Res.string.proximity_allow_bluetooth
        MobileWalletProximityRemediationAction.OpenApplicationSettings -> Res.string.proximity_open_app_settings
        MobileWalletProximityRemediationAction.EnableBluetooth -> Res.string.proximity_enable_bluetooth
        MobileWalletProximityRemediationAction.UseSupportedDevice -> Res.string.proximity_use_supported_device
        MobileWalletProximityRemediationAction.Retry -> Res.string.proximity_try_again
    }
)

@Composable
private fun MobileWalletProximityReaderAuthenticationValidity.displayName(): String = stringResource(
    when (this) {
        MobileWalletProximityReaderAuthenticationValidity.Absent -> Res.string.proximity_auth_absent
        MobileWalletProximityReaderAuthenticationValidity.Malformed -> Res.string.proximity_auth_malformed
        MobileWalletProximityReaderAuthenticationValidity.Invalid -> Res.string.proximity_auth_invalid
        MobileWalletProximityReaderAuthenticationValidity.Valid -> Res.string.proximity_auth_valid
    }
)

@Composable
private fun MobileWalletProximityReaderTrustState.displayName(): String = stringResource(
    when (this) {
        MobileWalletProximityReaderTrustState.NotEvaluated -> Res.string.proximity_trust_not_evaluated
        MobileWalletProximityReaderTrustState.ValidButUntrusted -> Res.string.proximity_trust_untrusted
        MobileWalletProximityReaderTrustState.Revoked -> Res.string.proximity_trust_revoked
        MobileWalletProximityReaderTrustState.Trusted -> Res.string.proximity_trust_trusted
    }
)

@Composable
private fun MobileWalletProximityReaderCertificatePathState.displayName(): String = stringResource(
    when (this) {
        MobileWalletProximityReaderCertificatePathState.NotEvaluated -> Res.string.proximity_not_evaluated
        MobileWalletProximityReaderCertificatePathState.UnknownAuthority -> Res.string.proximity_unknown_authority
        MobileWalletProximityReaderCertificatePathState.Invalid -> Res.string.proximity_auth_invalid
        MobileWalletProximityReaderCertificatePathState.Valid -> Res.string.proximity_auth_valid
    }
)

@Composable
private fun MobileWalletProximityReaderRevocationState.displayName(): String = stringResource(
    when (this) {
        MobileWalletProximityReaderRevocationState.NotChecked -> Res.string.proximity_not_checked
        MobileWalletProximityReaderRevocationState.Good -> Res.string.proximity_revocation_good
        MobileWalletProximityReaderRevocationState.Revoked -> Res.string.proximity_trust_revoked
        MobileWalletProximityReaderRevocationState.Indeterminate -> Res.string.proximity_indeterminate
    }
)

@Composable
private fun MobileWalletProximityRicalState.displayName(): String = stringResource(
    when (this) {
        MobileWalletProximityRicalState.NotEvaluated -> Res.string.proximity_not_evaluated
        MobileWalletProximityRicalState.Unavailable -> Res.string.proximity_unavailable
        MobileWalletProximityRicalState.Invalid -> Res.string.proximity_auth_invalid
        MobileWalletProximityRicalState.NoMatchingAuthority -> Res.string.proximity_no_matching_authority
        MobileWalletProximityRicalState.Matched -> Res.string.proximity_matched_authority
    }
)

@Composable
private fun MobileWalletProximityDeviceAuthenticationMethod.displayName(): String = stringResource(
    when (this) {
        MobileWalletProximityDeviceAuthenticationMethod.Signature -> Res.string.proximity_auth_signature
        MobileWalletProximityDeviceAuthenticationMethod.Mac -> Res.string.proximity_auth_mac
    }
)
