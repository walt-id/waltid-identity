package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletProximityAction
import id.walt.wallet2.mobile.MobileWalletProximityActionResult
import id.walt.wallet2.mobile.MobileWalletProximityActionType
import id.walt.wallet2.mobile.MobileWalletProximityConfiguration
import id.walt.wallet2.mobile.MobileWalletProximityDocumentSubmission
import id.walt.wallet2.mobile.MobileWalletProximityElementReference
import id.walt.wallet2.mobile.MobileWalletProximityError
import id.walt.wallet2.mobile.MobileWalletProximityErrorCategory
import id.walt.wallet2.mobile.MobileWalletProximityHostActionResult
import id.walt.wallet2.mobile.MobileWalletProximityRemediationAction
import id.walt.wallet2.mobile.MobileWalletProximityReview
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

/** One holder choice derived only from the current immutable SDK review. */
data class WalletDemoProximityDocumentSelection(
    val requestIndex: Int,
    val credentialId: String,
    val disclosedElements: Set<MobileWalletProximityElementReference>,
)

/** Shared Android/iOS Compose state around the SDK-owned protocol state. */
data class WalletDemoProximityUiState(
    val active: Boolean = false,
    val sessionState: MobileWalletProximityState? = null,
    val selections: List<WalletDemoProximityDocumentSelection> = emptyList(),
    val continueAfterResponse: Boolean = false,
    val hostActionInProgress: MobileWalletProximityRemediationAction? = null,
    val actionError: MobileWalletProximityError? = null,
) {
    val review: MobileWalletProximityReview?
        get() = (sessionState as? MobileWalletProximityState.ReviewRequired)?.review

    val canApprove: Boolean
        get() = review?.let { current ->
            selections.map { it.requestIndex }.toSet() == current.documents.map { it.requestIndex }.toSet() &&
                selections.all { it.disclosedElements.isNotEmpty() }
        } == true

    val isTerminal: Boolean
        get() = sessionState.isTerminal()
}

/** Performs one OS-owned prerequisite action and returns only its privacy-safe outcome. */
fun interface WalletDemoProximityHostActionExecutor {
    suspend fun perform(
        action: MobileWalletProximityRemediationAction,
    ): MobileWalletProximityHostActionResult
}

/**
 * Shared Compose journey controller. It owns UI choices and lifecycle only; the Wallet SDK remains
 * the single source of protocol state, trust facts, request meaning, and legal actions.
 */
class WalletDemoProximityController(
    private val wallet: ProximityPresentationBackend,
    private val configurationProvider: () -> MobileWalletProximityConfiguration = {
        MobileWalletProximityConfiguration()
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val mutableState = MutableStateFlow(WalletDemoProximityUiState())
    val state: StateFlow<WalletDemoProximityUiState> = mutableState.asStateFlow()

    private var session: MobileWalletProximitySession? = null
    private var sessionJob: Job? = null
    private var hostActionJob: Job? = null
    private var generation: Long = 0

    fun start() {
        if (mutableState.value.active) return
        generation += 1
        val startGeneration = generation
        mutableState.value = WalletDemoProximityUiState(active = true)
        sessionJob = scope.launch(dispatcher) {
            try {
                // Resolve exactly once per start: settings changes cannot mutate an active session.
                val started = wallet.startProximityPresentation(configurationProvider())
                if (!currentCoroutineContext().isActive ||
                    generation != startGeneration || !mutableState.value.active
                ) {
                    kotlinx.coroutines.withContext(NonCancellable) { started.close() }
                    return@launch
                }
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
                        sessionState = MobileWalletProximityState.Failed(demoSessionFailure),
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
                MobileWalletProximityElementReference(it.namespace, it.elementIdentifier)
            },
        )
        replaceSelection(selection)
    }

    fun toggleElement(requestIndex: Int, element: MobileWalletProximityElementReference) {
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
            MobileWalletProximityAction.Approve(
                MobileWalletProximitySubmission(
                    documents = review.documents.map { document ->
                        val selection = current.selections.single { it.requestIndex == document.requestIndex }
                        MobileWalletProximityDocumentSubmission(
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

    fun decline() = dispatch(MobileWalletProximityAction.Decline)

    fun retryPrerequisites() = dispatch(MobileWalletProximityAction.RetryPrerequisites)

    fun remediate(
        action: MobileWalletProximityRemediationAction,
        executor: WalletDemoProximityHostActionExecutor,
    ) {
        val capabilities = (mutableState.value.sessionState as? MobileWalletProximityState.CheckingPrerequisites)
            ?.capabilities ?: return
        if (action !in capabilities.remediationActions || mutableState.value.hostActionInProgress != null) return
        val currentSession = session ?: return
        mutableState.update { it.copy(hostActionInProgress = action, actionError = null) }
        val actionGeneration = generation
        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            try {
                val result = try {
                    executor.perform(action)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    MobileWalletProximityHostActionResult.Failed
                }
                if (generation != actionGeneration || !mutableState.value.active) return@launch
                val dispatchResult = currentSession.dispatch(
                    MobileWalletProximityAction.ReportRemediation(action, result)
                )
                if (generation != actionGeneration || !mutableState.value.active) return@launch
                mutableState.update {
                    it.copy(
                        hostActionInProgress = null,
                        actionError = (dispatchResult as? MobileWalletProximityActionResult.Rejected)?.error,
                    )
                }
            } finally {
                if (generation == actionGeneration) hostActionJob = null
            }
        }
        hostActionJob = job
        job.start()
    }

    fun cancel() {
        val current = mutableState.value.sessionState
        if (current == null) {
            dismiss()
            return
        }
        if (MobileWalletProximityActionType.Cancel in current.legalActions) {
            dispatch(MobileWalletProximityAction.Cancel)
        }
    }

    fun handleLifecycleInterruption() {
        val current = mutableState.value
        if (current.hostActionInProgress == null &&
            current.sessionState !is MobileWalletProximityState.CheckingPrerequisites
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
        mutableState.value = WalletDemoProximityUiState()
        if (closing != null) scope.launch(dispatcher) { closing.close() }
    }

    /** Closes a terminal session before starting a fresh capability check and exchange. */
    fun restart() {
        if (!mutableState.value.isTerminal) return
        dismiss()
        start()
    }

    private fun dispatch(action: MobileWalletProximityAction) {
        val currentSession = session ?: return
        val actionGeneration = generation
        mutableState.update { it.copy(actionError = null) }
        scope.launch(dispatcher) {
            val result = currentSession.dispatch(action)
            if (generation != actionGeneration || !mutableState.value.active) return@launch
            mutableState.update {
                it.copy(actionError = (result as? MobileWalletProximityActionResult.Rejected)?.error)
            }
        }
    }

    private fun publish(sessionState: MobileWalletProximityState) {
        mutableState.update { current ->
            val reviewForNewExchange = (sessionState as? MobileWalletProximityState.ReviewRequired)
                ?.review
                ?.takeIf { current.review?.exchange != it.exchange }
            current.copy(
                sessionState = sessionState,
                selections = reviewForNewExchange?.defaultSelections() ?: current.selections,
                continueAfterResponse = if (reviewForNewExchange != null) false else current.continueAfterResponse,
                actionError = null,
            )
        }
    }

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

private fun MobileWalletProximityReview.defaultSelections(): List<WalletDemoProximityDocumentSelection> =
    documents.map { document ->
        val credential = document.credentialOptions.first()
        WalletDemoProximityDocumentSelection(
            requestIndex = document.requestIndex,
            credentialId = credential.credentialId,
            disclosedElements = credential.requestedElements.mapTo(linkedSetOf()) {
                MobileWalletProximityElementReference(it.namespace, it.elementIdentifier)
            },
        )
    }

private fun MobileWalletProximityState?.isTerminal(): Boolean = when (this) {
    is MobileWalletProximityState.Completed,
    MobileWalletProximityState.Cancelled,
    is MobileWalletProximityState.Failed -> true
    else -> false
}

private val demoSessionFailure = MobileWalletProximityError(
    category = MobileWalletProximityErrorCategory.Internal,
    code = "demo_session_failed",
    message = "The in-person presentation could not be started",
    recoverable = true,
)
