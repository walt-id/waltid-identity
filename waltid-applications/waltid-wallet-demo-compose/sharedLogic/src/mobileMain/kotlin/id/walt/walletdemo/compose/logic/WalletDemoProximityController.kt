package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.ProximityConnectedRoute
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.wallet2.mobile.ProximityEngagementMethod
import id.walt.wallet2.mobile.ProximityAction
import id.walt.wallet2.mobile.ProximityActionResult
import id.walt.wallet2.mobile.ProximityActionType
import id.walt.wallet2.mobile.ProximityApproval
import id.walt.wallet2.mobile.ProximityPreparationResult
import id.walt.wallet2.mobile.ProximityPreparedSharing
import id.walt.wallet2.mobile.ProximitySharingPlan
import id.walt.wallet2.mobile.ProximityCapabilities
import id.walt.wallet2.mobile.ProximityBleBearerPolicy
import id.walt.wallet2.mobile.ProximityBleConfiguration
import id.walt.wallet2.mobile.ProximityBleRoles
import id.walt.wallet2.mobile.ProximityConfiguration
import id.walt.wallet2.mobile.ProximityDocumentSubmission
import id.walt.wallet2.mobile.ProximityElementReference
import id.walt.wallet2.mobile.ProximityError
import id.walt.wallet2.mobile.ProximityErrorCategory
import id.walt.wallet2.mobile.ProximityHostActionResult
import id.walt.wallet2.mobile.ProximitySessionConfiguration
import id.walt.wallet2.mobile.ProximityNfcHandover
import id.walt.wallet2.mobile.ProximityNfcRetrievalConfiguration
import id.walt.wallet2.mobile.ProximityRemediationAction
import id.walt.wallet2.mobile.ProximityRetrievalOptions
import id.walt.wallet2.mobile.ProximityReview
import id.walt.wallet2.mobile.ProximityRecovery
import id.walt.wallet2.mobile.ProximityReaderTrustSettings
import id.walt.wallet2.mobile.ProximitySession
import id.walt.wallet2.mobile.ProximityState
import id.walt.wallet2.mobile.ProximitySubmission
import id.walt.wallet2.mobile.legalActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One holder choice derived only from the current immutable SDK review. */
data class WalletDemoProximityDocumentSelection(
    val requestIndex: Int,
    val credentialId: String,
    val disclosedElements: Set<ProximityElementReference>,
)

/** Shared Android/iOS Compose state around the SDK-owned protocol state. */
data class WalletDemoProximityUiState(
    val active: Boolean = false,
    val sessionState: ProximityState? = null,
    val selections: List<WalletDemoProximityDocumentSelection> = emptyList(),
    val continueAfterResponse: Boolean = false,
    val hostActionInProgress: ProximityRemediationAction? = null,
    val actionError: ProximityError? = null,
    val capabilities: ProximityCapabilities? = null,
    val connectedRoute: ProximityConnectedRoute? = null,
    val automaticPermissionAttempts: Set<ProximityRemediationAction> = emptySet(),
    val preferredEngagement: ProximityEngagementMethod? = null,
    val nfcRequiresUserAction: Boolean = false,
    val approvalMode: WalletDemoProximityApprovalMode = WalletDemoProximityApprovalMode.AskEachTime,
    val preparedSharing: ProximityPreparedSharing? = null,
    val recentPlan: ProximitySharingPlan? = null,
    val refreshingEngagementChoices: List<ProximityEngagementMethod> = emptyList(),
) {
    val refreshingEngagement: Boolean get() = refreshingEngagementChoices.isNotEmpty()

    val showsEngagement: Boolean get() = sessionState is ProximityState.EngagementReady || refreshingEngagement

    /** Connection methods stay in place while a mode change replaces the session. */
    val engagementChoices: List<ProximityEngagementMethod>
        get() = (sessionState as? ProximityState.EngagementReady)?.engagements.orEmpty()
            .map { if (it is ProximityEngagement.Qr) ProximityEngagementMethod.Qr else ProximityEngagementMethod.Nfc }
            .distinct().sortedBy { it == ProximityEngagementMethod.Qr }
            .ifEmpty { refreshingEngagementChoices }

    val displayedEngagement: ProximityEngagementMethod?
        get() = preferredEngagement?.takeIf { it in engagementChoices }
            ?: engagementChoices.singleOrNull()?.takeUnless {
                it == ProximityEngagementMethod.Nfc && nfcRequiresUserAction
            }

    val qrVisible: Boolean get() = displayedEngagement == ProximityEngagementMethod.Qr

    val review: ProximityReview?
        get() = when (val state = sessionState) {
            is ProximityState.ReviewRequired -> state.review
            is ProximityState.PreparationRequired -> state.plan.review
            else -> null
        }

    val preparingApproval: Boolean get() = sessionState is ProximityState.PreparationRequired

    val canApprove: Boolean
        get() = review?.let { current ->
            selections.map { it.requestIndex }.toSet() == current.documents.map { it.requestIndex }.toSet() &&
                selections.all { selected -> selected.disclosedElements.isNotEmpty() &&
                    current.documents.single { it.requestIndex == selected.requestIndex }.requiredElements.all { it in selected.disclosedElements } }
        } == true

    /** Runtime permission the demo host must resolve before the SDK session may be created. */
    val automaticPermissionAction: ProximityRemediationAction?
        get() = (sessionState as? ProximityState.CheckingPrerequisites)
            ?.capabilities
            ?.automaticPermissionActions
            ?.firstOrNull { it !in automaticPermissionAttempts }

    val isTerminal: Boolean
        get() = sessionState.isTerminal()
}

/** Performs one OS-owned prerequisite action and returns only its privacy-safe outcome. */
fun interface WalletDemoProximityHostActionExecutor {
    suspend fun perform(
        action: ProximityRemediationAction,
    ): ProximityHostActionResult
}

/**
 * Shared Compose journey controller. It owns UI choices and lifecycle only; the Wallet SDK remains
 * the single source of protocol state, trust facts, request meaning, and legal actions.
 */
class WalletDemoProximityController(
    private val wallet: ProximityPresentationBackend,
    private val profileProvider: () -> WalletDemoProximityTransportProfile =
        { WalletDemoProximityTransportProfile.Default },
    private val readerTrustSettingsProvider: () -> ProximityReaderTrustSettings =
        { ProximityReaderTrustSettings() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val systemPresentationActive: () -> Boolean = { false },
    private val requestNfcPresentment: (() -> Unit)? = null,
    private val approvalModeProvider: () -> WalletDemoProximityApprovalMode = { WalletDemoProximityApprovalMode.AskEachTime },
) {
    private val mutableState = MutableStateFlow(initialUiState())
    val state: StateFlow<WalletDemoProximityUiState> = mutableState.asStateFlow()

    private var session: ProximitySession? = null
    private var closingJob: Job? = null
    private var effectiveConfiguration: ProximityConfiguration? = null
    private var pendingConfiguration: ProximityConfiguration? = null
    private var sessionJob: Job? = null
    private var hostActionJob: Job? = null
    private var generation: Long = 0
    private var preparedEngagementLaunched = false

    fun start() {
        if (mutableState.value.active) return
        val mode = approvalModeProvider()
        val configuration = readerTrustSettingsProvider().applyTo(
            profileProvider().configuration()
        ).copy(approval = mode.toApproval())
        generation += 1
        val startGeneration = generation
        pendingConfiguration = configuration
        effectiveConfiguration = configuration
        mutableState.value = initialUiState().copy(active = true, approvalMode = mode)
        checkPrerequisitesAndStart(configuration, startGeneration)
    }

    private fun checkPrerequisitesAndStart(
        configuration: ProximityConfiguration,
        startGeneration: Long,
    ) {
        sessionJob?.cancel()
        sessionJob = scope.launch(dispatcher) {
            try {
                closingJob?.join()
                if (!isCurrent(startGeneration)) return@launch
                val capabilities = wallet.proximityPresentationCapabilities(configuration)
                if (!isCurrent(startGeneration)) return@launch
                mutableState.update { it.copy(capabilities = capabilities) }
                publish(ProximityState.CheckingPrerequisites(capabilities))
                if (!isCurrent(startGeneration)) return@launch
                if (configuration.approval !is ProximityApproval.Prepared &&
                    (mutableState.value.automaticPermissionAction != null || !capabilities.mayStart)) return@launch

                val started = wallet.startProximityPresentation(configuration)
                if (!isCurrent(startGeneration)) {
                    withContext(NonCancellable) { started.close() }
                    return@launch
                }
                pendingConfiguration = null
                session = started
                started.state
                    .onEach {
                        currentCoroutineContext().ensureActive()
                        if (generation == startGeneration && mutableState.value.active) publish(it)
                    }
                    .first { it.isTerminal() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (generation != startGeneration || !mutableState.value.active) return@launch
                mutableState.update {
                    it.copy(
                        sessionState = ProximityState.Failed(demoSessionFailure),
                        refreshingEngagementChoices = emptyList(),
                        actionError = demoSessionFailure,
                    )
                }
            }
        }
    }

    fun selectCredential(requestIndex: Int, credentialId: String) {
        val review = mutableState.value.review ?: return
        val document = review.documents.singleOrNull { it.requestIndex == requestIndex } ?: return
        val credential = document.credentialOptions.singleOrNull { it.credentialId == credentialId } ?: return
        val selection = WalletDemoProximityDocumentSelection(
            requestIndex = requestIndex,
            credentialId = credentialId,
            disclosedElements = credential.requestedElements.mapTo(linkedSetOf()) {
                ProximityElementReference(it.namespace, it.elementIdentifier)
            },
        )
        replaceSelection(selection)
    }

    fun toggleElement(requestIndex: Int, element: ProximityElementReference) {
        val current = mutableState.value
        val review = current.review ?: return
        val selection = current.selections.singleOrNull { it.requestIndex == requestIndex } ?: return
        if (element in review.documents.single { it.requestIndex == requestIndex }.requiredElements) return
        val credential = review.documents.singleOrNull { it.requestIndex == requestIndex }
            ?.credentialOptions?.singleOrNull { it.credentialId == selection.credentialId }
            ?: return
        val offered = credential.requestedElements.any {
            it.namespace == element.namespace && it.elementIdentifier == element.elementIdentifier
        }
        if (!offered) return
        replaceSelection(
            selection.copy(
                disclosedElements = selection.disclosedElements.toMutableSet().apply {
                    if (!add(element)) remove(element)
                }.toSet(),
            )
        )
    }

    fun setContinueAfterResponse(enabled: Boolean) {
        if (mutableState.value.review == null || mutableState.value.preparingApproval) return
        mutableState.update { it.copy(continueAfterResponse = enabled, actionError = null) }
    }

    fun approve() {
        val current = mutableState.value
        val review = current.review ?: return
        if (!current.canApprove) return
        val submission = ProximitySubmission(
            documents = review.documents.map { document ->
                val selection = current.selections.single { it.requestIndex == document.requestIndex }
                ProximityDocumentSubmission(
                    requestIndex = selection.requestIndex,
                    credentialId = selection.credentialId,
                    disclosedElements = selection.disclosedElements,
                )
            },
            continueAfterResponse = current.continueAfterResponse,
        )
        val preparation = current.sessionState as? ProximityState.PreparationRequired
        if (preparation != null) {
            when (val result = preparation.plan.approve(submission)) {
                is ProximityPreparationResult.Rejected -> mutableState.update { it.copy(actionError = result.error) }
                is ProximityPreparationResult.Prepared -> {
                    val configuration = effectiveConfiguration ?: return
                    replaceSession(configuration.copy(approval = ProximityApproval.Prepared(result.sharing)))
                }
            }
        } else dispatch(ProximityAction.Approve(review.reviewId, submission))
    }

    fun decline() {
        if (mutableState.value.preparingApproval) { dismiss(); return }
        val review = mutableState.value.review ?: return
        dispatch(ProximityAction.Decline(review.reviewId))
    }

    fun retryPrerequisites() {
        val configuration = pendingConfiguration
        if (session == null && configuration != null) {
            if (mutableState.value.hostActionInProgress == null) {
                checkPrerequisitesAndStart(configuration, generation)
            }
        } else {
            dispatch(ProximityAction.RetryPrerequisites)
        }
    }

    /** Skips optional permission setup only when the SDK has proved a complete alternative route. */
    fun continueWithAvailableConnection() {
        val current = mutableState.value
        val capabilities = (current.sessionState as? ProximityState.CheckingPrerequisites)?.capabilities ?: return
        if (!capabilities.mayStart || current.hostActionInProgress != null) return
        mutableState.update { it.copy(automaticPermissionAttempts = it.automaticPermissionAttempts + capabilities.automaticPermissionActions) }
        retryPrerequisites()
    }

    fun remediate(
        action: ProximityRemediationAction,
        executor: WalletDemoProximityHostActionExecutor,
    ) {
        val current = mutableState.value
        val terminalError = (current.sessionState as? ProximityState.Failed)?.error
        if (terminalError != null && action in terminalError.remediationActions) {
            val configuration = effectiveConfiguration ?: return
            replaceSession(configuration.copy(approval = mutableState.value.approvalMode.toApproval()), action, executor)
            return
        }
        val capabilities = (current.sessionState as? ProximityState.CheckingPrerequisites)
            ?.capabilities ?: return
        if (action !in capabilities.remediationActions || mutableState.value.hostActionInProgress != null) return
        val currentSession = session
        val configuration = pendingConfiguration
        if (currentSession == null && configuration == null) return
        mutableState.update { it.copy(
            hostActionInProgress = action, actionError = null,
            automaticPermissionAttempts = it.automaticPermissionAttempts + action,
        ) }
        val actionGeneration = generation
        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            try {
                val result = try {
                    executor.perform(action)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    ProximityHostActionResult.Failed
                }
                if (generation != actionGeneration || !mutableState.value.active) return@launch
                if (currentSession == null) {
                    mutableState.update { it.copy(hostActionInProgress = null) }
                    checkPrerequisitesAndStart(requireNotNull(configuration), actionGeneration)
                } else {
                    val dispatchResult = currentSession.dispatch(
                        ProximityAction.ReportRemediation(action, result)
                    )
                    if (generation != actionGeneration || !mutableState.value.active) return@launch
                    mutableState.update {
                        it.copy(
                            hostActionInProgress = null,
                            actionError = (dispatchResult as? ProximityActionResult.Rejected)?.error,
                        )
                    }
                }
            } finally {
                if (generation == actionGeneration) hostActionJob = null
            }
        }
        hostActionJob = job
        job.start()
    }

    fun cancel() {
        if (mutableState.value.preparingApproval) { dismiss(); return }
        if (session == null) {
            dismiss()
            return
        }
        val current = mutableState.value.sessionState
        if (current == null) {
            dismiss()
            return
        }
        if (ProximityActionType.Cancel in current.legalActions) {
            dispatch(ProximityAction.Cancel)
        }
    }

    fun handleLifecycleInterruption() {
        if (systemPresentationActive()) return
        val current = mutableState.value
        if (current.preparedSharing != null) { dismiss(); return }
        if (current.hostActionInProgress == null &&
            current.sessionState !is ProximityState.CheckingPrerequisites
        ) {
            cancel()
        }
    }

    fun dismiss() {
        generation += 1
        sessionJob?.cancel()
        sessionJob = null
        hostActionJob?.cancel()
        hostActionJob = null
        val closing = session
        val revoking = mutableState.value.preparedSharing
        if (revoking != null) scope.launch(dispatcher) { revoking.revoke() }
        session = null
        pendingConfiguration = null
        effectiveConfiguration = null
        mutableState.value = initialUiState()
        scheduleClose(closing)
    }

    /** Closes a terminal session before starting a fresh capability check and exchange. */
    fun restart() {
        if (!mutableState.value.isTerminal) return
        if (effectiveConfiguration?.approval is ProximityApproval.Prepared && reviewRecentRequest()) return
        val mode = approvalModeProvider()
        val configuration = readerTrustSettingsProvider().applyTo(profileProvider().configuration())
        mutableState.update { it.copy(approvalMode = mode) }
        replaceSession(configuration.copy(approval = mode.toApproval()))
    }

    /** Shows the recent request again; only the subsequent Approve button can issue another grant. */
    fun reviewRecentRequest(): Boolean {
        val plan = mutableState.value.recentPlan?.takeUnless { it.isExpired } ?: return false
        if (!mutableState.value.isTerminal) return false
        publish(ProximityState.PreparationRequired(plan))
        mutableState.update { it.copy(preparedSharing = null, selections = plan.review.defaultSelections()) }
        return true
    }

    /** Applies saved preferences before connection. It never creates or changes a disclosure approval. */
    fun refreshPreferences() {
        val mode = approvalModeProvider()
        val current = mutableState.value
        val previous = effectiveConfiguration ?: return
        val awaitingPrerequisite = (current.sessionState as? ProximityState.CheckingPrerequisites)?.let {
            !it.capabilities.mayStart || current.automaticPermissionAction != null
        } == true
        if (!current.active || current.preparedSharing != null || current.hostActionInProgress != null ||
            (current.sessionState !is ProximityState.EngagementReady && !awaitingPrerequisite)) return
        val configuration = readerTrustSettingsProvider().applyTo(profileProvider().configuration())
            .copy(approval = mode.toApproval())
        val sameTransport = previous.session == configuration.session
        if (sameTransport && current.approvalMode == mode) return
        mutableState.update { it.copy(approvalMode = mode) }
        // Only an unchanged transport can retain its already prepared choice layout.
        replaceSession(configuration, preserveEngagement = sameTransport && current.showsEngagement)
    }

    /** Reveals the prepared engagement and requests platform NFC UI after an explicit user choice. */
    fun showEngagement(method: ProximityEngagementMethod) {
        val current = mutableState.value
        if (current.sessionState !is ProximityState.EngagementReady || current.hostActionInProgress != null || method !in current.engagementChoices) return
        mutableState.update { current ->
            if (current.hostActionInProgress == null && method in current.engagementChoices) {
                current.copy(preferredEngagement = method)
            } else current
        }
        if (method == ProximityEngagementMethod.Nfc) requestNfcPresentment?.invoke()
    }

    private fun replaceSession(
        configuration: ProximityConfiguration,
        action: ProximityRemediationAction? = null,
        executor: WalletDemoProximityHostActionExecutor? = null,
        preserveEngagement: Boolean = false,
    ) {
        generation += 1
        val nextGeneration = generation
        sessionJob?.cancel()
        hostActionJob?.cancel()
        val closing = session
        session = null
        effectiveConfiguration = configuration
        pendingConfiguration = configuration
        val previous = mutableState.value
        val nextApproval = (configuration.approval as? ProximityApproval.Prepared)?.sharing
        previous.preparedSharing?.takeIf { it !== nextApproval }?.let { oldApproval ->
            scope.launch(dispatcher) { oldApproval.revoke() }
        }
        preparedEngagementLaunched = false
        mutableState.value = initialUiState().copy(
            active = true, hostActionInProgress = action, approvalMode = previous.approvalMode,
            recentPlan = previous.recentPlan,
            preparedSharing = nextApproval,
            preferredEngagement = if (preserveEngagement) previous.displayedEngagement.takeUnless {
                // A replacement iOS NFC sheet needs a fresh explicit choice.
                it == ProximityEngagementMethod.Nfc && previous.nfcRequiresUserAction
            }
                else previous.connectedRoute?.engagement.takeIf { configuration.approval is ProximityApproval.Prepared },
            refreshingEngagementChoices = if (preserveEngagement) previous.engagementChoices else emptyList(),
        )
        scheduleClose(closing)
        sessionJob = scope.launch(dispatcher) {
            closingJob?.join()
            if (!isCurrent(nextGeneration)) return@launch
            if (action != null && executor != null) {
                try {
                    executor.perform(action)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Rechecking reports the current safe platform error and guidance.
                }
                if (!isCurrent(nextGeneration)) return@launch
                mutableState.update { it.copy(
                    hostActionInProgress = null, automaticPermissionAttempts = setOf(action),
                ) }
            }
            checkPrerequisitesAndStart(configuration, nextGeneration)
        }
    }

    private fun scheduleClose(closing: ProximitySession?) {
        if (closing == null) return
        val previous = closingJob
        closingJob = scope.launch(dispatcher) {
            withContext(NonCancellable) {
                previous?.join()
                closing.close()
            }
        }
    }

    private fun dispatch(action: ProximityAction) {
        val currentSession = session ?: return
        val actionGeneration = generation
        mutableState.update { it.copy(actionError = null) }
        scope.launch(dispatcher) {
            val result = currentSession.dispatch(action)
            if (generation != actionGeneration || !mutableState.value.active) return@launch
            mutableState.update {
                it.copy(actionError = (result as? ProximityActionResult.Rejected)?.error)
            }
        }
    }

    private suspend fun isCurrent(expectedGeneration: Long): Boolean =
        currentCoroutineContext().isActive && generation == expectedGeneration && mutableState.value.active

    private fun publish(sessionState: ProximityState) {
        mutableState.update { current ->
            val reviewForNewExchange = when (sessionState) {
                is ProximityState.ReviewRequired -> sessionState.review
                is ProximityState.PreparationRequired -> sessionState.plan.review
                else -> null
            }?.takeIf { current.review?.reviewId != it.reviewId }
            current.copy(
                sessionState = sessionState,
                refreshingEngagementChoices = current.refreshingEngagementChoices.takeIf {
                    sessionState is ProximityState.Preparing ||
                        (sessionState is ProximityState.CheckingPrerequisites && sessionState.capabilities.mayStart &&
                            sessionState.capabilities.automaticPermissionActions.isEmpty())
                }.orEmpty(),
                capabilities = (sessionState as? ProximityState.CheckingPrerequisites)?.capabilities ?: current.capabilities,
                connectedRoute = session?.connectedRoute ?: current.connectedRoute,
                recentPlan = (sessionState as? ProximityState.PreparationRequired)?.plan ?: session?.sharingPlan ?: current.recentPlan,
                selections = reviewForNewExchange?.defaultSelections() ?: current.selections,
                continueAfterResponse = if (reviewForNewExchange != null) false else current.continueAfterResponse,
                actionError = null,
            )
        }
        refreshPreferences()
        val current = mutableState.value
        if (sessionState is ProximityState.EngagementReady && current.preparedSharing != null && !preparedEngagementLaunched) {
            current.preferredEngagement?.takeIf { it in current.engagementChoices }?.let {
                // Approve and get ready also authorizes reopening the connection the holder just reviewed.
                preparedEngagementLaunched = true
                showEngagement(it)
            }
        }
    }

    private fun initialUiState() = WalletDemoProximityUiState(nfcRequiresUserAction = requestNfcPresentment != null)

    private fun replaceSelection(selection: WalletDemoProximityDocumentSelection) {
        mutableState.update { current ->
            current.copy(
                selections = (current.selections.filterNot { it.requestIndex == selection.requestIndex } + selection)
                    .sortedBy(WalletDemoProximityDocumentSelection::requestIndex),
                actionError = null,
            )
        }
    }
}

private val ProximityCapabilities.automaticPermissionActions:
    List<ProximityRemediationAction>
    get() = remediationActions.filter {
        it == ProximityRemediationAction.RequestBluetoothPermission ||
            it == ProximityRemediationAction.RequestNearbyWifiPermission ||
            it == ProximityRemediationAction.RequestLocalNetworkPermission
    }

internal fun WalletDemoProximityTransportProfile.configuration(): ProximityConfiguration =
    when (this) {
        WalletDemoProximityTransportProfile.Default,
        WalletDemoProximityTransportProfile.Bluetooth,
        WalletDemoProximityTransportProfile.WifiAware -> {
            val retrieval = ProximityRetrievalOptions(
                bluetoothLowEnergy = ProximityBleConfiguration().takeUnless {
                    this == WalletDemoProximityTransportProfile.WifiAware
                },
                nfc = ProximityNfcRetrievalConfiguration().takeIf {
                    this == WalletDemoProximityTransportProfile.Default
                },
                wifiAware = this != WalletDemoProximityTransportProfile.Bluetooth,
            )
            ProximityConfiguration(
                session = ProximitySessionConfiguration.ConventionalNfc(
                    handover = ProximityNfcHandover.Negotiated,
                    retrieval = retrieval,
                    qrFallback = retrieval,
                ),
            )
        }
        WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid ->
            ProximityConfiguration(
                session = ProximitySessionConfiguration.ProvisionalNfcV2(
                    bluetoothLowEnergy = ProximityBleConfiguration(
                        roles = ProximityBleRoles.CentralClient,
                        bearerPolicy = ProximityBleBearerPolicy.GattOnly,
                    ),
                ),
            )
        WalletDemoProximityTransportProfile.ProvisionalNfcV2WifiAware ->
            ProximityConfiguration(
                session = ProximitySessionConfiguration.ProvisionalNfcV2(wifiAware = true),
            )
        WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct ->
            ProximityConfiguration(
                session = ProximitySessionConfiguration.ProvisionalNfcV2(),
            )
    }

private fun ProximityReview.defaultSelections(): List<WalletDemoProximityDocumentSelection> =
    documents.mapNotNull { document ->
        val credential = document.credentialOptions.singleOrNull() ?: return@mapNotNull null
        WalletDemoProximityDocumentSelection(
            requestIndex = document.requestIndex,
            credentialId = credential.credentialId,
            disclosedElements = credential.requestedElements.mapTo(linkedSetOf()) {
                ProximityElementReference(it.namespace, it.elementIdentifier)
            },
        )
    }

private fun ProximityState?.isTerminal(): Boolean = when (this) {
    is ProximityState.PreparationRequired,
    is ProximityState.Completed,
    is ProximityState.NoData,
    ProximityState.Cancelled,
    is ProximityState.Failed -> true
    else -> false
}

private fun WalletDemoProximityApprovalMode.toApproval(): ProximityApproval = when (this) {
    WalletDemoProximityApprovalMode.AskEachTime -> ProximityApproval.AskEachTime
    WalletDemoProximityApprovalMode.PrepareSharing -> ProximityApproval.PrepareBeforeSharing
}

private val demoSessionFailure = ProximityError(
    category = ProximityErrorCategory.Internal,
    code = "demo_session_failed",
    message = "The in-person presentation could not be started",
    recovery = ProximityRecovery.StartNewSession,
)
