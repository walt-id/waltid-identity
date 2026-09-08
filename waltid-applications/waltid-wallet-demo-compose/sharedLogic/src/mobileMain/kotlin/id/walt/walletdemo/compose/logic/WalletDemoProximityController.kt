package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletProximityConnectedRoute
import id.walt.wallet2.mobile.MobileWalletProximityEngagement
import id.walt.wallet2.mobile.MobileWalletProximityEngagementMethod
import id.walt.wallet2.mobile.MobileWalletProximityAction
import id.walt.wallet2.mobile.MobileWalletProximityActionResult
import id.walt.wallet2.mobile.MobileWalletProximityActionType
import id.walt.wallet2.mobile.MobileWalletProximityCapabilities
import id.walt.wallet2.mobile.MobileWalletProximityBleBearerPolicy
import id.walt.wallet2.mobile.MobileWalletProximityBleConfiguration
import id.walt.wallet2.mobile.MobileWalletProximityBleRoles
import id.walt.wallet2.mobile.MobileWalletProximityConfiguration
import id.walt.wallet2.mobile.MobileWalletProximityDocumentSubmission
import id.walt.wallet2.mobile.MobileWalletProximityElementReference
import id.walt.wallet2.mobile.MobileWalletProximityError
import id.walt.wallet2.mobile.MobileWalletProximityErrorCategory
import id.walt.wallet2.mobile.MobileWalletProximityHostActionResult
import id.walt.wallet2.mobile.MobileWalletProximitySessionConfiguration
import id.walt.wallet2.mobile.MobileWalletProximityNfcHandover
import id.walt.wallet2.mobile.MobileWalletProximityNfcRetrievalConfiguration
import id.walt.wallet2.mobile.MobileWalletProximityRemediationAction
import id.walt.wallet2.mobile.MobileWalletProximityConventionalRetrievalConfiguration
import id.walt.wallet2.mobile.MobileWalletProximityReview
import id.walt.wallet2.mobile.MobileWalletProximityRecovery
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustSettings
import id.walt.wallet2.mobile.MobileWalletProximitySession
import id.walt.wallet2.mobile.MobileWalletProximityState
import id.walt.wallet2.mobile.MobileWalletProximitySubmission
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
    val hostActionInProgress: MobileWalletProximityRemediationAction? = null,
    val actionError: MobileWalletProximityError? = null,
    val capabilities: MobileWalletProximityCapabilities? = null,
    val connectedRoute: MobileWalletProximityConnectedRoute? = null,
    val automaticPermissionAttempts: Set<MobileWalletProximityRemediationAction> = emptySet(),
    val preferredEngagement: MobileWalletProximityEngagementMethod? = null,
    val nfcRequiresUserAction: Boolean = false,
) {
    /** Only prepared SDK engagements are offered as ready user actions. */
    val engagementChoices: List<MobileWalletProximityEngagementMethod>
        get() = (sessionState as? MobileWalletProximityState.EngagementReady)?.engagements.orEmpty()
            .map { if (it is MobileWalletProximityEngagement.Qr) MobileWalletProximityEngagementMethod.Qr else MobileWalletProximityEngagementMethod.Nfc }
            .distinct().sortedBy { it == MobileWalletProximityEngagementMethod.Qr }

    val displayedEngagement: MobileWalletProximityEngagementMethod?
        get() = preferredEngagement?.takeIf { it in engagementChoices }
            ?: engagementChoices.singleOrNull()?.takeUnless {
                it == MobileWalletProximityEngagementMethod.Nfc && nfcRequiresUserAction
            }

    val qrVisible: Boolean get() = displayedEngagement == MobileWalletProximityEngagementMethod.Qr

    val review: MobileWalletProximityReview?
        get() = (sessionState as? MobileWalletProximityState.ReviewRequired)?.review

    val canApprove: Boolean
        get() = review?.let { current ->
            selections.map { it.requestIndex }.toSet() == current.documents.map { it.requestIndex }.toSet() &&
                selections.all { it.disclosedElements.isNotEmpty() }
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
    private val readerTrustSettingsProvider: () -> MobileWalletProximityReaderTrustSettings =
        { MobileWalletProximityReaderTrustSettings() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val systemPresentationActive: () -> Boolean = { false },
    private val requestNfcPresentment: (() -> Unit)? = null,
) {
    private val mutableState = MutableStateFlow(initialUiState())
    val state: StateFlow<WalletDemoProximityUiState> = mutableState.asStateFlow()

    private var session: MobileWalletProximitySession? = null
    private var closingJob: Job? = null
    private var effectiveConfiguration: MobileWalletProximityConfiguration? = null
    private var pendingConfiguration: MobileWalletProximityConfiguration? = null
    private var sessionJob: Job? = null
    private var hostActionJob: Job? = null
    private var generation: Long = 0

    fun start() {
        if (mutableState.value.active) return
        val configuration = readerTrustSettingsProvider().applyTo(
            profileProvider().configuration()
        )
        generation += 1
        val startGeneration = generation
        pendingConfiguration = configuration
        effectiveConfiguration = configuration
        mutableState.value = initialUiState().copy(active = true)
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
                publish(MobileWalletProximityState.CheckingPrerequisites(capabilities))
                if (mutableState.value.automaticPermissionAction != null || !capabilities.mayStart) return@launch

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
        if (mutableState.value.review == null) return
        mutableState.update { it.copy(continueAfterResponse = enabled, actionError = null) }
    }

    fun approve() {
        val current = mutableState.value
        val review = current.review ?: return
        if (!current.canApprove) return
        dispatch(
            ProximityAction.Approve(
                reviewId = review.reviewId,
                submission = ProximitySubmission(
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
            )
        )
    }

    fun decline() {
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
        val capabilities = (current.sessionState as? MobileWalletProximityState.CheckingPrerequisites)?.capabilities ?: return
        if (!capabilities.mayStart || current.hostActionInProgress != null) return
        mutableState.update { it.copy(automaticPermissionAttempts = it.automaticPermissionAttempts + capabilities.automaticPermissionActions) }
        retryPrerequisites()
    }

    fun remediate(
        action: ProximityRemediationAction,
        executor: WalletDemoProximityHostActionExecutor,
    ) {
        val current = mutableState.value
        val terminalError = (current.sessionState as? MobileWalletProximityState.Failed)?.error
        if (terminalError != null && action in terminalError.remediationActions) {
            replaceSession(effectiveConfiguration ?: return, action, executor)
            return
        }
        val capabilities = (current.sessionState as? MobileWalletProximityState.CheckingPrerequisites)
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
        session = null
        pendingConfiguration = null
        effectiveConfiguration = null
        mutableState.value = initialUiState()
        scheduleClose(closing)
    }

    /** Closes a terminal session before starting a fresh capability check and exchange. */
    fun restart() {
        if (!mutableState.value.isTerminal) return
        replaceSession(effectiveConfiguration ?: return)
    }

    /** Reveals the prepared engagement and requests platform NFC UI after an explicit user choice. */
    fun showEngagement(method: MobileWalletProximityEngagementMethod) {
        val current = mutableState.value
        if (current.hostActionInProgress != null || method !in current.engagementChoices) return
        mutableState.update { current ->
            if (current.hostActionInProgress == null && method in current.engagementChoices) {
                current.copy(preferredEngagement = method)
            } else current
        }
        if (method == MobileWalletProximityEngagementMethod.Nfc) requestNfcPresentment?.invoke()
    }

    private fun replaceSession(
        configuration: MobileWalletProximityConfiguration,
        action: MobileWalletProximityRemediationAction? = null,
        executor: WalletDemoProximityHostActionExecutor? = null,
    ) {
        generation += 1
        val nextGeneration = generation
        sessionJob?.cancel()
        hostActionJob?.cancel()
        val closing = session
        session = null
        effectiveConfiguration = configuration
        pendingConfiguration = configuration
        mutableState.value = initialUiState().copy(active = true, hostActionInProgress = action)
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

    private fun scheduleClose(closing: MobileWalletProximitySession?) {
        if (closing == null) return
        val previous = closingJob
        closingJob = scope.launch(dispatcher) {
            kotlinx.coroutines.withContext(NonCancellable) {
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
            val reviewForNewExchange = (sessionState as? ProximityState.ReviewRequired)
                ?.review
                ?.takeIf { current.review?.reviewId != it.reviewId }
            current.copy(
                sessionState = sessionState,
                capabilities = (sessionState as? MobileWalletProximityState.CheckingPrerequisites)?.capabilities ?: current.capabilities,
                connectedRoute = session?.connectedRoute ?: current.connectedRoute,
                selections = reviewForNewExchange?.defaultSelections() ?: current.selections,
                continueAfterResponse = if (reviewForNewExchange != null) false else current.continueAfterResponse,
                actionError = null,
            )
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

private val MobileWalletProximityCapabilities.automaticPermissionActions:
    List<MobileWalletProximityRemediationAction>
    get() = remediationActions.filter {
        it == MobileWalletProximityRemediationAction.RequestBluetoothPermission
    }

internal fun WalletDemoProximityTransportProfile.configuration(): MobileWalletProximityConfiguration =
    when (this) {
        WalletDemoProximityTransportProfile.Default,
        WalletDemoProximityTransportProfile.Bluetooth -> {
            val retrieval = MobileWalletProximityConventionalRetrievalConfiguration(
                nfc = MobileWalletProximityNfcRetrievalConfiguration().takeIf {
                    this == WalletDemoProximityTransportProfile.Default
                },
            )
            MobileWalletProximityConfiguration(
                session = MobileWalletProximitySessionConfiguration.ConventionalNfc(
                    handover = MobileWalletProximityNfcHandover.Negotiated,
                    retrieval = retrieval,
                    qrFallback = retrieval,
                ),
            )
        }
        WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid ->
            MobileWalletProximityConfiguration(
                session = MobileWalletProximitySessionConfiguration.ProvisionalNfcV2(
                    bluetoothLowEnergy = MobileWalletProximityBleConfiguration(
                        roles = MobileWalletProximityBleRoles.CentralClient,
                        bearerPolicy = MobileWalletProximityBleBearerPolicy.GattOnly,
                    ),
                ),
            )
        WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct ->
            MobileWalletProximityConfiguration(
                session = MobileWalletProximitySessionConfiguration.ProvisionalNfcV2(),
            )
    }

private fun MobileWalletProximityReview.defaultSelections(): List<WalletDemoProximityDocumentSelection> =
    documents.map { document ->
        val credential = document.credentialOptions.first()
        WalletDemoProximityDocumentSelection(
            requestIndex = document.requestIndex,
            credentialId = credential.credentialId,
            disclosedElements = credential.requestedElements.mapTo(linkedSetOf()) {
                ProximityElementReference(it.namespace, it.elementIdentifier)
            },
        )
    }

private fun ProximityState?.isTerminal(): Boolean = when (this) {
    is ProximityState.Completed,
    is ProximityState.NoData,
    ProximityState.Cancelled,
    is ProximityState.Failed -> true
    else -> false
}

private val demoSessionFailure = ProximityError(
    category = ProximityErrorCategory.Internal,
    code = "demo_session_failed",
    message = "The in-person presentation could not be started",
    recovery = ProximityRecovery.StartNewSession,
)
