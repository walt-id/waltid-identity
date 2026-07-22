package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletDemoController(
    private val wallet: DemoWallet,
    private val pinStore: DemoPinStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var receiveJob: Job? = null
    private var presentationJob: Job? = null
    private val _state = MutableStateFlow(
        WalletDemoUiState(
            auth = readInitialAuthState(),
        ),
    )
    val state: StateFlow<WalletDemoUiState> = _state.asStateFlow()

    fun updatePin(value: String) {
        _state.update { state ->
            when (val auth = state.auth) {
                is WalletAuthState.Setup -> state.copy(auth = auth.copy(pin = value, error = null))
                is WalletAuthState.Login -> state.copy(auth = auth.copy(pin = value, error = null))
                is WalletAuthState.StorageUnavailable,
                WalletAuthState.Unlocked -> state
            }
        }
    }

    fun updatePinConfirmation(value: String) {
        _state.update { state ->
            when (val auth = state.auth) {
                is WalletAuthState.Setup -> state.copy(auth = auth.copy(confirmation = value, error = null))
                is WalletAuthState.Login,
                is WalletAuthState.StorageUnavailable,
                WalletAuthState.Unlocked,
                -> state
            }
        }
    }

    fun submitPin() {
        if (_state.value.isAuthenticating) return
        when (val auth = _state.value.auth) {
            is WalletAuthState.Setup -> submitSetupPin(auth)
            is WalletAuthState.Login -> submitLoginPin(auth)
            is WalletAuthState.StorageUnavailable,
            WalletAuthState.Unlocked -> Unit
        }
    }

    fun retryPinStorage() {
        _state.update { state ->
            if (state.auth is WalletAuthState.StorageUnavailable) {
                state.copy(auth = readInitialAuthState())
            } else {
                state
            }
        }
    }

    fun lock() {
        receiveJob?.cancel()
        presentationJob?.cancel()
        val previous = getAndUpdateState {
            it.copy(
                auth = WalletAuthState.Login(),
                operation = WalletOperationState.Idle,
                interaction = WalletInteractionState.Idle,
                requestDrafts = it.requestDrafts.copy(txCode = ""),
                offerPreview = null,
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
                presentationReview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                presentationCompleted = false,
                pendingPresentationContinuation = null,
                presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
            )
        }
        discardIssuancePreview(previous.activeIssuancePreviewHandle())
        discardPresentationPreview(previous.activePresentationPreviewHandle())
    }

    fun selectTab(tab: WalletDemoTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun completePresentationContinuation() {
        _state.update { state ->
            val pending = state.pendingPresentationContinuation ?: return@update state
            state.copy(
                operation = WalletOperationState.Succeeded(
                    message = pending.successMessage,
                    tab = WalletDemoTab.Present,
                ),
                interaction = WalletInteractionState.Success(
                    WalletInteractionKind.Present,
                    WalletInteractionSuccessOutcome.InformationShared,
                    pending.successMessage,
                ),
                presentationCompleted = true,
                pendingPresentationContinuation = null,
            )
        }
    }

    fun failPresentationContinuation(reason: String) {
        _state.update { state ->
            if (state.pendingPresentationContinuation == null) return@update state
            state.copy(
                operation = WalletOperationState.Failed(
                    message = WalletDisplayText.failure(WalletDisplayText.PresentationContinuationFailed, reason),
                    tab = WalletDemoTab.Present,
                ),
                interaction = WalletInteractionState.Failure(
                    WalletInteractionKind.Present,
                    WalletDisplayText.failure(WalletDisplayText.PresentationContinuationFailed, reason),
                    recoverable = true,
                ),
                presentationCompleted = false,
                pendingPresentationContinuation = null,
            )
        }
    }

    fun startReceiveCapture() = startCapture(WalletInteractionKind.Receive)

    fun startPresentCapture() = startCapture(WalletInteractionKind.Present)

    private fun startCapture(kind: WalletInteractionKind) {
        val current = _state.value
        if (current.auth != WalletAuthState.Unlocked || current.session !is WalletSessionState.Ready) return
        if (current.interaction !is WalletInteractionState.Idle &&
            current.interaction !is WalletInteractionState.LocalCancellation
        ) return
        _state.update {
            it.copy(
                selectedTab = kind.toLegacyTab(),
                interaction = WalletInteractionState.Capturing(kind),
                operation = WalletOperationState.Idle,
                requestDrafts = when (kind) {
                    WalletInteractionKind.Receive -> it.requestDrafts.copy(offerUrl = "", txCode = "")
                    WalletInteractionKind.Present -> it.requestDrafts.copy(presentationRequestUrl = "")
                },
                lastReceivedCredentialIds = emptyList(),
                receiveCompleted = false,
                presentationCompleted = false,
            )
        }
    }

    fun showManualEntry() {
        _state.update { current ->
            val kind = current.interaction.kindOrNull ?: return@update current
            current.copy(interaction = WalletInteractionState.Capturing(kind, WalletCaptureMode.Manual))
        }
    }

    fun showScanner() {
        _state.update { current ->
            val kind = current.interaction.kindOrNull ?: return@update current
            current.copy(interaction = WalletInteractionState.Capturing(kind, WalletCaptureMode.Scanner))
        }
    }

    fun submitCapturedRequest(
        value: String,
        source: WalletRequestSource = WalletRequestSource.Qr,
    ) {
        val current = _state.value
        val expected = when (val interaction = current.interaction) {
            is WalletInteractionState.Capturing -> interaction.kind
            is WalletInteractionState.Failure -> interaction.kind.takeIf { interaction.recoverable }
            else -> null
        } ?: return
        val trimmed = value.trim()
        val detectedKind = trimmed.toInteractionKind()
        if (detectedKind == null) {
            _state.update {
                it.copy(
                    interaction = WalletInteractionState.Capturing(
                        kind = expected,
                        mode = if (source == WalletRequestSource.Manual) WalletCaptureMode.Manual else WalletCaptureMode.Scanner,
                        error = "This QR code or link is not a supported credential offer or presentation request.",
                    )
                )
            }
            return
        }
        val request = WalletIncomingRequest(detectedKind, trimmed, source)
        if (detectedKind != expected) {
            _state.update { it.copy(interaction = WalletInteractionState.WrongRequestType(expected, request)) }
            return
        }
        resolveIncomingRequest(request)
    }

    fun switchToDetectedRequest() {
        val wrongType = _state.value.interaction as? WalletInteractionState.WrongRequestType ?: return
        resolveIncomingRequest(wrongType.detected)
    }

    fun retryInteraction() {
        val current = _state.value
        val failure = current.interaction as? WalletInteractionState.Failure ?: return
        if (!failure.recoverable) return
        val value = when (failure.kind) {
            WalletInteractionKind.Receive -> current.requestDrafts.offerUrl
            WalletInteractionKind.Present -> current.requestDrafts.presentationRequestUrl
        }
        submitCapturedRequest(value, WalletRequestSource.Manual)
    }
    fun updateOfferUrl(value: String) {
        receiveJob?.cancel()
        val previous = getAndUpdateState {
            it.copy(
                requestDrafts = it.requestDrafts.copy(
                    offerUrl = value,
                    txCode = "",
                ),
                offerPreview = null,
                lastReceivedCredentialIds = emptyList(),
                receiveCompleted = false,
                operation = WalletOperationState.Idle,
            )
        }
        discardIssuancePreview(previous.activeIssuancePreviewHandle())
    }

    fun updateTxCode(value: String) {
        _state.update {
            val normalized = it.offerPreview?.transactionCode?.normalizeInput(value) ?: value
            it.copy(requestDrafts = it.requestDrafts.copy(txCode = normalized))
        }
    }

    fun updatePresentationRequestUrl(value: String) {
        presentationJob?.cancel()
        val previous = getAndUpdateState {
            it.copy(
                requestDrafts = it.requestDrafts.copy(presentationRequestUrl = value),
                presentationReview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                presentationCompleted = false,
                pendingPresentationContinuation = null,
                presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
                operation = if (
                    it.operation == WalletOperationState.ResolvingPresentation ||
                    it.operation == WalletOperationState.Presenting ||
                    it.operation == WalletOperationState.DecliningPresentation
                ) {
                    WalletOperationState.Idle
                } else {
                    it.operation
                },
            )
        }
        discardPresentationPreview(previous.activePresentationPreviewHandle())
    }

    fun handleDeepLink(url: String) {
        val kind = url.toInteractionKind() ?: return
        val incoming = WalletIncomingRequest(kind, url.trim(), WalletRequestSource.DeepLink)
        val current = _state.value
        if (current.auth != WalletAuthState.Unlocked || current.session !is WalletSessionState.Ready) {
            _state.update { it.copy(pendingIncomingRequest = incoming) }
            return
        }
        if (current.interaction !is WalletInteractionState.Idle &&
            current.interaction !is WalletInteractionState.LocalCancellation &&
            current.interaction !is WalletInteractionState.Success &&
            current.interaction !is WalletInteractionState.Failure
        ) {
            val resolving = current.interaction as? WalletInteractionState.Resolving
            if (resolving?.request != incoming) {
                _state.update { it.copy(replacementRequest = incoming) }
            }
            return
        }
        resolveIncomingRequest(incoming)
    }

    fun keepCurrentRequest() {
        _state.update { it.copy(replacementRequest = null) }
    }

    fun replaceCurrentRequest() {
        val incoming = _state.value.replacementRequest ?: return
        receiveJob?.cancel()
        presentationJob?.cancel()
        val previous = getAndUpdateState {
            it.copy(
                replacementRequest = null,
                interaction = WalletInteractionState.Idle,
                operation = WalletOperationState.Idle,
                offerPreview = null,
                presentationReview = null,
                requestDrafts = it.requestDrafts.copy(txCode = ""),
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                pendingPresentationContinuation = null,
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
                presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
            )
        }
        discardIssuancePreview(previous.activeIssuancePreviewHandle())
        discardPresentationPreview(previous.activePresentationPreviewHandle())
        resolveIncomingRequest(incoming)
    }

    fun cancelInteraction() {
        receiveJob?.cancel()
        presentationJob?.cancel()
        val current = _state.value
        val kind = current.interaction.kindOrNull ?: current.selectedTab.toInteractionKind()
        val previous = getAndUpdateState {
            it.copy(
                interaction = WalletInteractionState.LocalCancellation(kind),
                operation = WalletOperationState.Idle,
                requestDrafts = WalletRequestDrafts(),
                offerPreview = null,
                presentationReview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                receiveCompleted = false,
                presentationCompleted = false,
                pendingPresentationContinuation = null,
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
                presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
            )
        }
        discardIssuancePreview(previous.activeIssuancePreviewHandle())
        discardPresentationPreview(previous.activePresentationPreviewHandle())
    }

    fun finishInteraction() {
        _state.update {
            it.copy(
                interaction = WalletInteractionState.Idle,
                operation = WalletOperationState.Idle,
                requestDrafts = WalletRequestDrafts(),
                lastReceivedCredentialIds = emptyList(),
                receiveCompleted = false,
                presentationReview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                presentationCompleted = false,
                pendingPresentationContinuation = null,
            )
        }
    }

    private fun resolveIncomingRequest(request: WalletIncomingRequest) {
        val current = _state.value
        if (current.auth != WalletAuthState.Unlocked || current.session !is WalletSessionState.Ready) {
            _state.update { it.copy(pendingIncomingRequest = request) }
            return
        }
        _state.update {
            it.copy(
                pendingIncomingRequest = null,
                replacementRequest = null,
                selectedTab = request.kind.toLegacyTab(),
                interaction = WalletInteractionState.Validating(request),
                operation = WalletOperationState.Idle,
                requestDrafts = when (request.kind) {
                    WalletInteractionKind.Receive -> it.requestDrafts.copy(offerUrl = request.value, txCode = "")
                    WalletInteractionKind.Present -> it.requestDrafts.copy(presentationRequestUrl = request.value)
                },
                lastReceivedCredentialIds = emptyList(),
                receiveCompleted = false,
                presentationCompleted = false,
            )
        }
        when (request.kind) {
            WalletInteractionKind.Receive -> previewOffer()
            WalletInteractionKind.Present -> previewPresentation()
        }
    }

    fun startNewReceiveFlow() {
        receiveJob?.cancel()
        val previous = getAndUpdateState {
            it.copy(
                requestDrafts = it.requestDrafts.copy(
                    offerUrl = "",
                    txCode = "",
                ),
                offerPreview = null,
                lastReceivedCredentialIds = emptyList(),
                receiveCompleted = false,
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
                operation = WalletOperationState.Idle,
                interaction = WalletInteractionState.Capturing(WalletInteractionKind.Receive),
            )
        }
        discardIssuancePreview(previous.activeIssuancePreviewHandle())
    }

    fun startNewPresentationFlow() {
        presentationJob?.cancel()
        val previous = getAndUpdateState {
            it.copy(
                requestDrafts = it.requestDrafts.copy(presentationRequestUrl = ""),
                presentationReview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                presentationCompleted = false,
                pendingPresentationContinuation = null,
                presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
                operation = WalletOperationState.Idle,
                interaction = WalletInteractionState.Capturing(WalletInteractionKind.Present),
            )
        }
        discardPresentationPreview(previous.activePresentationPreviewHandle())
    }

    fun previewOffer() {
        val current = _state.value
        val offerUrl = current.requestDrafts.offerUrl.trim()
        if (!current.receiveActionEnabled || offerUrl.isBlank()) return
        val request = ReceiveRequest(offerUrl, current.receiveNavigationResetKey)
        val incoming = (current.interaction as? WalletInteractionState.Validating)?.request
            ?: WalletIncomingRequest(WalletInteractionKind.Receive, offerUrl, WalletRequestSource.Manual)
        if (!_state.compareAndSet(
                current,
                current.copy(
                    operation = WalletOperationState.ResolvingOffer,
                    interaction = WalletInteractionState.Resolving(incoming),
                )
            )
        ) return

        receiveJob = scope.launch(dispatcher) {
            var resolution: WalletDemoOfferPreview? = null
            try {
                val resolvedOffer = wallet.resolveOffer(offerUrl)
                resolution = resolvedOffer
                currentCoroutineContext().ensureActive()
                val installed = updateIfCurrent(request, WalletOperationState.ResolvingOffer) {
                    it.copy(
                        offerPreview = resolution,
                        operation = WalletOperationState.OfferPreview,
                        interaction = WalletInteractionState.ReviewingOffer(resolvedOffer.previewHandle),
                    )
                }
                if (!installed) {
                    wallet.discardIssuancePreview(resolvedOffer.previewHandle)
                }
                resolution = null
            } catch (cancellation: CancellationException) {
                resolution?.let { preview ->
                    withContext(NonCancellable) {
                        runCatching { wallet.discardIssuancePreview(preview.previewHandle) }
                    }
                }
                throw cancellation
            } catch (error: Throwable) {
                resolution?.let { preview ->
                    withContext(NonCancellable) {
                        runCatching { wallet.discardIssuancePreview(preview.previewHandle) }
                    }
                }
                updateIfCurrent(request, WalletOperationState.ResolvingOffer) {
                    it.copy(
                        operation = WalletOperationState.Failed(
                            message = WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, error),
                            tab = WalletDemoTab.Receive,
                        ),
                        interaction = WalletInteractionState.Failure(
                            kind = WalletInteractionKind.Receive,
                            message = WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, error),
                            recoverable = true,
                        ),
                    )
                }
            }
        }
    }

    fun acceptOffer() {
        val current = _state.value
        val ready = current.session as? WalletSessionState.Ready ?: return
        if (!current.acceptOfferEnabled) return
        val preview = current.offerPreview ?: return
        val offerUrl = current.requestDrafts.offerUrl.trim()
        val txCode = current.requestDrafts.txCode.trim().ifBlank { null }
        val request = ReceiveRequest(offerUrl, current.receiveNavigationResetKey)
        if (!_state.compareAndSet(
                current,
                current.copy(
                    operation = WalletOperationState.Receiving,
                    interaction = WalletInteractionState.Submitting(WalletInteractionKind.Receive),
                )
            )
        ) return

        receiveJob = scope.launch(dispatcher) {
            try {
                receiveCredential(ready, request, preview.previewHandle, txCode)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updateIfCurrent(request, WalletOperationState.Receiving) {
                    it.copy(
                        operation = WalletOperationState.Failed(
                            message = WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, error),
                            tab = WalletDemoTab.Receive,
                        ),
                        interaction = WalletInteractionState.Failure(
                            kind = WalletInteractionKind.Receive,
                            message = WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, error),
                            recoverable = true,
                        ),
                    )
                }
            }
        }
    }

    fun declineOffer() {
        val previewHandle = _state.value.offerPreview?.previewHandle ?: return
        _state.update {
            it.copy(
                offerPreview = null,
                requestDrafts = it.requestDrafts.copy(txCode = ""),
                operation = WalletOperationState.Succeeded(
                    message = WalletDisplayText.CredentialOfferDeclined,
                    tab = WalletDemoTab.Receive,
                ),
                interaction = WalletInteractionState.Success(
                    WalletInteractionKind.Receive,
                    WalletInteractionSuccessOutcome.OfferDeclined,
                    WalletDisplayText.CredentialOfferDeclined,
                ),
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
            )
        }
        scope.launch(dispatcher) {
            runCatching { wallet.discardIssuancePreview(previewHandle) }
                .onFailure { error -> setOperationError(WalletDisplayText.ReceiveFailed, error, WalletDemoTab.Receive) }
        }
    }

    private suspend fun receiveCredential(
        ready: WalletSessionState.Ready,
        request: ReceiveRequest,
        previewHandle: WalletDemoIssuancePreviewHandle,
        txCode: String?,
    ) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(request)) return
        val ids = wallet.receive(previewHandle, txCode)
        currentCoroutineContext().ensureActive()
        if (!isCurrent(request)) return
        val credentials = wallet.listCredentials()
        currentCoroutineContext().ensureActive()
        if (!isCurrent(request)) return
        val receivedCredentialIds = resolvedReceivedCredentialIds(
            returnedCredentialIds = ids,
            previousCredentials = ready.credentials,
            refreshedCredentials = credentials,
        )
        val displayableReceivedCredentialIds = receivedCredentialIds
            .filter { receivedCredentialId -> credentials.any { it.id == receivedCredentialId } }
        if (displayableReceivedCredentialIds.isEmpty()) {
            updateIfCurrent(request, WalletOperationState.Receiving) {
                it.copy(
                    session = ready.copy(credentials = credentials),
                    operation = WalletOperationState.Failed(
                        message = WalletDisplayText.failure(
                            WalletDisplayText.ReceiveFailed,
                            WalletDisplayText.ReceivedCredentialsUnavailable,
                        ),
                        tab = WalletDemoTab.Receive,
                    ),
                    interaction = WalletInteractionState.Failure(
                        WalletInteractionKind.Receive,
                        WalletDisplayText.failure(
                            WalletDisplayText.ReceiveFailed,
                            WalletDisplayText.ReceivedCredentialsUnavailable,
                        ),
                        recoverable = false,
                    ),
                    offerPreview = null,
                    lastReceivedCredentialIds = emptyList(),
                    receiveCompleted = false,
                )
            }
            return
        }

        updateIfCurrent(request, WalletOperationState.Receiving) {
            it.copy(
                session = ready.copy(credentials = credentials),
                offerPreview = null,
                operation = WalletOperationState.Succeeded(
                    message = WalletDisplayText.receivedCredentials(displayableReceivedCredentialIds.size),
                    tab = WalletDemoTab.Receive,
                ),
                interaction = WalletInteractionState.Success(
                    WalletInteractionKind.Receive,
                    WalletInteractionSuccessOutcome.CredentialAdded,
                    WalletDisplayText.receivedCredentials(displayableReceivedCredentialIds.size),
                ),
                requestDrafts = it.requestDrafts.copy(txCode = ""),
                lastReceivedCredentialIds = displayableReceivedCredentialIds,
                receiveCompleted = true,
            )
        }
    }

    private fun isCurrent(request: ReceiveRequest): Boolean =
        _state.value.let {
            it.receiveNavigationResetKey == request.navigationResetKey &&
                it.requestDrafts.offerUrl.trim() == request.offerUrl
        }

    private inline fun updateIfCurrent(
        request: ReceiveRequest,
        expectedOperation: WalletOperationState,
        transform: (WalletDemoUiState) -> WalletDemoUiState,
    ): Boolean {
        while (true) {
            val current = _state.value
            if (
                current.receiveNavigationResetKey != request.navigationResetKey ||
                current.requestDrafts.offerUrl.trim() != request.offerUrl ||
                current.operation != expectedOperation
            ) {
                return false
            }
            if (_state.compareAndSet(current, transform(current))) return true
        }
    }

    private data class ReceiveRequest(
        val offerUrl: String,
        val navigationResetKey: Int,
    )

    fun present() {
        val current = _state.value
        val ready = current.session as? WalletSessionState.Ready ?: return
        val requestUrl = current.requestDrafts.presentationRequestUrl.trim()
        if (requestUrl.isBlank()) return

        scope.launch(dispatcher) {
            _state.update { it.copy(operation = WalletOperationState.Presenting) }
            runCatching {
                wallet.present(requestUrl, ready.did)
            }.onSuccess { result ->
                _state.update { it.withPresentationResult(result) }
            }.onFailure { error ->
                setOperationError(WalletDisplayText.PresentFailed, error, WalletDemoTab.Present)
            }
        }
    }

    fun previewPresentation() {
        val current = _state.value
        current.session as? WalletSessionState.Ready ?: return
        val requestUrl = current.requestDrafts.presentationRequestUrl.trim()
        if (
            requestUrl.isBlank() ||
            current.presentationReview != null ||
            current.presentationCompleted ||
            current.isBusy
        ) {
            return
        }
        val request = PresentationRequest(requestUrl, current.presentationNavigationResetKey)
        val resolving = current.copy(
            operation = WalletOperationState.ResolvingPresentation,
            interaction = WalletInteractionState.Resolving(
                (current.interaction as? WalletInteractionState.Validating)?.request
                    ?: WalletIncomingRequest(
                        WalletInteractionKind.Present,
                        requestUrl,
                        WalletRequestSource.Manual,
                    )
            ),
            selectedPresentationCredentialOptions = emptySet(),
            selectedPresentationDisclosureOptions = emptySet(),
        )
        if (!_state.compareAndSet(current, resolving)) return

        presentationJob = scope.launch(dispatcher) {
            var preview: WalletDemoPresentationPreviewResult? = null
            try {
                val resolvedPreview = wallet.previewPresentation(requestUrl)
                preview = resolvedPreview
                currentCoroutineContext().ensureActive()
                val installed = updatePresentationIfCurrent(request, WalletOperationState.ResolvingPresentation) {
                    it.copy(
                        operation = WalletOperationState.Idle,
                        interaction = WalletInteractionState.ReviewingPresentation(resolvedPreview.previewHandle()),
                        presentationReview = resolvedPreview,
                        selectedPresentationCredentialOptions = when (resolvedPreview) {
                            is WalletDemoPresentationPreviewResult.Ready ->
                                resolvedPreview.preview.defaultCredentialSelection()
                            is WalletDemoPresentationPreviewResult.Invalid -> emptySet()
                        },
                        selectedPresentationDisclosureOptions = emptySet(),
                    )
                }
                if (!installed) {
                    wallet.discardPresentationPreview(resolvedPreview.previewHandle())
                }
                preview = null
            } catch (cancellation: CancellationException) {
                preview?.let { resolvedPreview ->
                    withContext(NonCancellable) {
                        runCatching { wallet.discardPresentationPreview(resolvedPreview.previewHandle()) }
                    }
                }
                throw cancellation
            } catch (error: Throwable) {
                preview?.let { resolvedPreview ->
                    withContext(NonCancellable) {
                        runCatching { wallet.discardPresentationPreview(resolvedPreview.previewHandle()) }
                    }
                }
                updatePresentationIfCurrent(request, WalletOperationState.ResolvingPresentation) {
                    it.copy(
                        operation = WalletOperationState.Failed(
                            message = WalletDisplayText.failure(WalletDisplayText.PreviewFailed, error),
                            tab = WalletDemoTab.Present,
                        ),
                        interaction = WalletInteractionState.Failure(
                            WalletInteractionKind.Present,
                            WalletDisplayText.failure(WalletDisplayText.PreviewFailed, error),
                            recoverable = true,
                        ),
                    )
                }
            }
        }
    }

    private inline fun updatePresentationIfCurrent(
        request: PresentationRequest,
        expectedOperation: WalletOperationState,
        transform: (WalletDemoUiState) -> WalletDemoUiState,
    ): Boolean {
        while (true) {
            val current = _state.value
            if (
                current.presentationNavigationResetKey != request.navigationResetKey ||
                current.requestDrafts.presentationRequestUrl.trim() != request.requestUrl ||
                current.operation != expectedOperation
            ) {
                return false
            }
            if (_state.compareAndSet(current, transform(current))) return true
        }
    }

    private data class PresentationRequest(
        val requestUrl: String,
        val navigationResetKey: Int,
    )

    fun togglePresentationCredential(selection: WalletDemoPresentationCredentialSelection) {
        _state.update { state ->
            val selected = state.selectedPresentationCredentialOptions
            val option = state.presentationPreview
                ?.credentialOptions
                ?.firstOrNull { it.selection == selection }
            val nextCredentials = if (selection in selected) {
                selected - selection
            } else {
                if (option?.multiple == true) {
                    selected + selection
                } else {
                    selected
                        .filterNot { it.queryId == selection.queryId }
                        .toSet() + selection
                }
            }
            val retainedDisclosures = if (option?.multiple == true) {
                state.selectedPresentationDisclosureOptions
                    .filterNot { it.queryId == selection.queryId && it.credentialId == selection.credentialId }
                    .toSet()
            } else {
                state.selectedPresentationDisclosureOptions
                    .filterNot { it.queryId == selection.queryId }
                    .toSet()
            }
            state.copy(
                selectedPresentationCredentialOptions = nextCredentials,
                selectedPresentationDisclosureOptions = retainedDisclosures.forSelectedCredentials(nextCredentials),
            )
        }
    }

    fun togglePresentationDisclosure(selection: WalletDemoPresentationDisclosureSelection) {
        _state.update { state ->
            val selected = state.selectedPresentationDisclosureOptions
            state.copy(
                selectedPresentationDisclosureOptions = if (selection in selected) {
                    selected - selection
                } else {
                    selected + selection
                }.forSelectedCredentials(state.selectedPresentationCredentialOptions)
            )
        }
    }

    fun submitPresentation() {
        val current = _state.value
        val ready = current.session as? WalletSessionState.Ready ?: return
        if (!current.presentationReviewEnabled) return
        val requestUrl = current.requestDrafts.presentationRequestUrl.trim()
        val previewHandle = current.presentationPreview?.previewHandle ?: return
        val selectedCredentialOptions = current.selectedPresentationCredentialOptions.toList()
        val selectedDisclosureOptions = current.selectedPresentationDisclosureOptions
            .forSelectedCredentials(current.selectedPresentationCredentialOptions)
            .toList()
        if (requestUrl.isBlank()) return
        if (!current.presentationCredentialSelectionComplete()) {
            _state.compareAndSet(
                current,
                current.copy(
                    operation = WalletOperationState.Failed(
                        WalletDisplayText.failure(
                            WalletDisplayText.PresentFailed,
                            WalletDisplayText.SelectCredentialForEveryRequest,
                        ),
                        WalletDemoTab.Present,
                    )
                ),
            )
            return
        }
        val request = PresentationRequest(requestUrl, current.presentationNavigationResetKey)
        if (!_state.compareAndSet(
                current,
                current.copy(
                    operation = WalletOperationState.Presenting,
                    interaction = WalletInteractionState.Submitting(WalletInteractionKind.Present),
                )
            )
        ) return

        presentationJob = scope.launch(dispatcher) {
            try {
                val result = wallet.submitPresentation(
                    previewHandle,
                    selectedCredentialOptions,
                    selectedDisclosureOptions,
                    ready.did,
                )
                currentCoroutineContext().ensureActive()
                updatePresentationIfCurrent(request, WalletOperationState.Presenting) {
                    it.withPresentationResult(
                        result = result,
                        clearPreview = true,
                        clearSelections = true,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updatePresentationIfCurrent(request, WalletOperationState.Presenting) {
                    it.copy(
                        operation = WalletOperationState.Failed(
                            message = WalletDisplayText.failure(WalletDisplayText.PresentFailed, error),
                            tab = WalletDemoTab.Present,
                        ),
                        presentationReview = null,
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = false,
                        interaction = WalletInteractionState.Failure(
                            WalletInteractionKind.Present,
                            WalletDisplayText.failure(WalletDisplayText.PresentFailed, error),
                            recoverable = true,
                        ),
                    )
                }
            }
        }
    }

    private fun WalletDemoUiState.withPresentationResult(
        result: WalletDemoOperationResult,
        clearPreview: Boolean = false,
        clearSelections: Boolean = false,
        resetNavigation: Boolean = false,
        successOutcome: WalletInteractionSuccessOutcome = WalletInteractionSuccessOutcome.InformationShared,
        failureRecoverable: Boolean = true,
    ): WalletDemoUiState {
        val success = result as? WalletDemoOperationResult.Success
        val pending = success?.continuation?.let { continuation ->
            WalletDemoPendingPresentationContinuation(
                continuation = continuation,
                successMessage = success.message,
            )
        }

        return copy(
            operation = when {
                result is WalletDemoOperationResult.Failure -> WalletOperationState.Failed(
                    message = result.message,
                    tab = WalletDemoTab.Present,
                )
                pending != null -> operation
                else -> WalletOperationState.Succeeded(
                    message = success!!.message,
                    tab = WalletDemoTab.Present,
                )
            },
            interaction = when {
                result is WalletDemoOperationResult.Failure -> WalletInteractionState.Failure(
                    WalletInteractionKind.Present,
                    result.message,
                    recoverable = failureRecoverable,
                )
                pending != null -> interaction
                else -> WalletInteractionState.Success(
                    WalletInteractionKind.Present,
                    successOutcome,
                    success!!.message,
                )
            },
            presentationReview = if (clearPreview) null else presentationReview,
            selectedPresentationCredentialOptions =
                if (clearSelections) emptySet() else selectedPresentationCredentialOptions,
            selectedPresentationDisclosureOptions =
                if (clearSelections) emptySet() else selectedPresentationDisclosureOptions,
            presentationCompleted = success != null && pending == null,
            pendingPresentationContinuation = pending,
            presentationNavigationResetKey =
                if (resetNavigation) presentationNavigationResetKey + 1 else presentationNavigationResetKey,
        )
    }

    fun cancelPresentationReview() {
        val current = _state.value
        if (!current.presentationReviewEnabled) return
        val previewHandle = current.activePresentationPreviewHandle() ?: return
        if (!_state.compareAndSet(
                current,
                current.copy(
                    operation = WalletOperationState.Succeeded(
                        message = WalletDisplayText.PresentationReviewCancelled,
                        tab = WalletDemoTab.Present,
                    ),
                    interaction = WalletInteractionState.LocalCancellation(WalletInteractionKind.Present),
                    presentationReview = null,
                    selectedPresentationCredentialOptions = emptySet(),
                    selectedPresentationDisclosureOptions = emptySet(),
                    presentationCompleted = false,
                    presentationNavigationResetKey = current.presentationNavigationResetKey + 1,
                ),
            )
        ) return
        presentationJob?.cancel()
        scope.launch(dispatcher) {
            runCatching { wallet.discardPresentationPreview(previewHandle) }
                .onFailure { error -> setOperationError(WalletDisplayText.PresentFailed, error, WalletDemoTab.Present) }
        }
    }

    fun rejectPresentation() {
        val current = _state.value
        current.session as? WalletSessionState.Ready ?: return
        if (!current.presentationReviewEnabled) return
        val previewHandle = current.activePresentationPreviewHandle() ?: return
        val isReportingError = current.presentationReview is WalletDemoPresentationPreviewResult.Invalid
        val request = PresentationRequest(
            current.requestDrafts.presentationRequestUrl.trim(),
            current.presentationNavigationResetKey,
        )
        if (!_state.compareAndSet(
                current,
                current.copy(
                    operation = WalletOperationState.DecliningPresentation,
                    interaction = WalletInteractionState.Declining(WalletInteractionKind.Present),
                )
            )
        ) return

        presentationJob = scope.launch(dispatcher) {
            try {
                val result = wallet.rejectPresentation(previewHandle)
                currentCoroutineContext().ensureActive()
                updatePresentationIfCurrent(request, WalletOperationState.DecliningPresentation) {
                    it.withPresentationResult(
                        result = if (isReportingError && result is WalletDemoOperationResult.Success) {
                            result.copy(message = WalletDisplayText.VerifierNotified)
                        } else {
                            result
                        },
                        clearPreview = true,
                        clearSelections = true,
                        resetNavigation = true,
                        successOutcome = WalletInteractionSuccessOutcome.PresentationRejected,
                        failureRecoverable = false,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updatePresentationIfCurrent(request, WalletOperationState.DecliningPresentation) {
                    it.copy(
                        operation = WalletOperationState.Failed(
                            message = WalletDisplayText.failure(WalletDisplayText.RejectFailed, error),
                            tab = WalletDemoTab.Present,
                        ),
                        presentationReview = null,
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = false,
                        interaction = WalletInteractionState.Failure(
                            WalletInteractionKind.Present,
                            WalletDisplayText.failure(WalletDisplayText.PresentFailed, error),
                            recoverable = false,
                        ),
                        presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
                    )
                }
            }
        }
    }

    private fun discardIssuancePreview(previewHandle: WalletDemoIssuancePreviewHandle?) {
        if (previewHandle == null) return
        scope.launch(dispatcher) {
            runCatching { wallet.discardIssuancePreview(previewHandle) }
        }
    }

    private fun WalletDemoUiState.activeIssuancePreviewHandle(): WalletDemoIssuancePreviewHandle? =
        offerPreview
            ?.takeUnless { receiveCompleted }
            ?.previewHandle

    private fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle?) {
        if (previewHandle == null) return
        scope.launch(dispatcher) {
            runCatching { wallet.discardPresentationPreview(previewHandle) }
        }
    }

    private fun WalletDemoUiState.activePresentationPreviewHandle(): WalletDemoPresentationPreviewHandle? =
        takeUnless { presentationCompleted }?.presentationReview?.previewHandle()

    private fun WalletDemoPresentationPreviewResult.previewHandle(): WalletDemoPresentationPreviewHandle =
        when (this) {
            is WalletDemoPresentationPreviewResult.Ready -> preview.previewHandle
            is WalletDemoPresentationPreviewResult.Invalid -> error.previewHandle
        }

    private inline fun getAndUpdateState(
        transform: (WalletDemoUiState) -> WalletDemoUiState,
    ): WalletDemoUiState {
        while (true) {
            val previous = _state.value
            if (_state.compareAndSet(previous, transform(previous))) return previous
        }
    }

    private fun submitSetupPin(auth: WalletAuthState.Setup) {
        val pin = auth.pin
        if (!isValidPin(pin)) {
            setSetupPinError(WalletDisplayText.PinMustContain4To8Digits)
            return
        }

        if (pin != auth.confirmation) {
            setSetupPinError(WalletDisplayText.PinConfirmationDoesNotMatch)
            return
        }

        _state.update { it.copy(isAuthenticating = true) }
        scope.launch(dispatcher) {
            runCatching { pinStore.setPin(pin) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            auth = WalletAuthState.Unlocked,
                            isAuthenticating = false,
                        )
                    }
                    bootstrapIfNeeded()
                }
                .onFailure {
                    setSetupPinError("PIN could not be saved")
                }
        }
    }

    private fun submitLoginPin(auth: WalletAuthState.Login) {
        val pin = auth.pin
        if (!isValidPin(pin)) {
            setLoginPinError(WalletDisplayText.PinMustContain4To8Digits)
            return
        }

        _state.update { it.copy(isAuthenticating = true) }
        scope.launch(dispatcher) {
            runCatching { pinStore.verifyPin(pin) }
                .onSuccess { matches ->
                    if (!matches) {
                        setLoginPinError(WalletDisplayText.WrongPin)
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            auth = WalletAuthState.Unlocked,
                            isAuthenticating = false,
                        )
                    }
                    bootstrapIfNeeded()
                }
                .onFailure {
                    setLoginPinError("PIN could not be verified")
                }
        }
    }

    private fun bootstrapIfNeeded() {
        if (_state.value.session is WalletSessionState.Ready) {
            resolvePendingIncomingRequestIfReady()
            return
        }
        if (_state.value.session is WalletSessionState.Bootstrapping) {
            return
        }

        scope.launch(dispatcher) {
            _state.update {
                it.copy(
                    session = WalletSessionState.Bootstrapping,
                    operation = WalletOperationState.Idle,
                )
            }
            runCatching {
                val result = wallet.bootstrap()
                val credentials = wallet.listCredentials()
                result to credentials
            }.onSuccess { (result, credentials) ->
                _state.update {
                    it.copy(
                        session = WalletSessionState.Ready(
                            did = result.did,
                            credentials = credentials,
                        ),
                        operation = WalletOperationState.Idle,
                        warning = result.warning,
                    )
                }
                resolvePendingIncomingRequestIfReady()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        session = WalletSessionState.Failed(WalletDisplayText.failure(WalletDisplayText.BootstrapFailed, error)),
                        operation = WalletOperationState.Idle,
                    )
                }
            }
        }
    }

    private fun setSetupPinError(message: String) {
        _state.update { state ->
            val auth = state.auth as? WalletAuthState.Setup ?: return@update state
            state.copy(
                auth = auth.copy(error = message),
                isAuthenticating = false,
            )
        }
    }

    private fun setLoginPinError(message: String) {
        _state.update { state ->
            val auth = state.auth as? WalletAuthState.Login ?: return@update state
            state.copy(
                auth = auth.copy(error = message),
                isAuthenticating = false,
            )
        }
    }

    private fun setOperationError(prefix: String, error: Throwable, tab: WalletDemoTab) {
        _state.update {
            it.copy(
                operation = WalletOperationState.Failed(
                    message = WalletDisplayText.failure(prefix, error),
                    tab = tab,
                ),
                interaction = WalletInteractionState.Failure(
                    kind = tab.toInteractionKind(),
                    message = WalletDisplayText.failure(prefix, error),
                    recoverable = true,
                ),
            )
        }
    }

    private fun resolvePendingIncomingRequestIfReady() {
        val current = _state.value
        val pending = current.pendingIncomingRequest ?: return
        if (current.auth != WalletAuthState.Unlocked || current.session !is WalletSessionState.Ready) return
        resolveIncomingRequest(pending)
    }

    private fun readInitialAuthState(): WalletAuthState =
        runCatching {
            if (pinStore.hasPin()) WalletAuthState.Login() else WalletAuthState.Setup()
        }.getOrElse {
            WalletAuthState.StorageUnavailable()
        }

    private companion object {
        val pinPattern = Regex("\\d{4,8}")

        fun isValidPin(pin: String): Boolean = pin.matches(pinPattern)
    }
}
