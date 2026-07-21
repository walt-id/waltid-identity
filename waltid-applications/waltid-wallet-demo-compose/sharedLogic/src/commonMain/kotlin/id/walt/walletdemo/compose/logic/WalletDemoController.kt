package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WalletDemoController(
    private val wallet: DemoWallet,
    private val pinStore: DemoPinStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var receiveJob: Job? = null
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
        _state.update {
            it.copy(
                auth = WalletAuthState.Login(),
                operation = WalletOperationState.Idle,
                requestDrafts = it.requestDrafts.copy(txCode = ""),
                offerPreview = null,
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
            )
        }
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
                presentationCompleted = false,
                pendingPresentationContinuation = null,
            )
        }
    }

    fun updateOfferUrl(value: String) {
        receiveJob?.cancel()
        discardIssuancePreview(activeIssuancePreviewHandle())
        _state.update {
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
    }

    fun updateTxCode(value: String) {
        _state.update {
            val normalized = it.offerPreview?.transactionCode?.normalizeInput(value) ?: value
            it.copy(requestDrafts = it.requestDrafts.copy(txCode = normalized))
        }
    }

    fun updatePresentationRequestUrl(value: String) {
        discardPresentationPreview(activePresentationPreviewHandle())
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(presentationRequestUrl = value),
                presentationReview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                presentationCompleted = false,
                pendingPresentationContinuation = null,
            )
        }
    }

    fun handleDeepLink(url: String) {
        when (WalletDeepLinkScheme.parse(url)) {
            WalletDeepLinkScheme.CredentialOffer -> {
                receiveJob?.cancel()
                discardIssuancePreview(activeIssuancePreviewHandle())
                discardPresentationPreview(activePresentationPreviewHandle())
                _state.update {
                    it.copy(
                        selectedTab = WalletDemoTab.Receive,
                        requestDrafts = it.requestDrafts.copy(
                            offerUrl = url,
                            txCode = "",
                        ),
                        offerPreview = null,
                        lastReceivedCredentialIds = emptyList(),
                        receiveCompleted = false,
                        receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
                        presentationReview = null,
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = false,
                        pendingPresentationContinuation = null,
                        operation = WalletOperationState.Idle,
                    )
                }
            }
            WalletDeepLinkScheme.PresentationRequest -> {
                receiveJob?.cancel()
                discardIssuancePreview(activeIssuancePreviewHandle())
                discardPresentationPreview(activePresentationPreviewHandle())
                _state.update {
                    it.copy(
                        selectedTab = WalletDemoTab.Present,
                        requestDrafts = it.requestDrafts.copy(presentationRequestUrl = url),
                        presentationReview = null,
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = false,
                        pendingPresentationContinuation = null,
                        presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
                        operation = WalletOperationState.Idle,
                    )
                }
            }
            null -> Unit
        }
    }

    fun startNewReceiveFlow() {
        receiveJob?.cancel()
        discardIssuancePreview(activeIssuancePreviewHandle())
        _state.update {
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
            )
        }
    }

    fun startNewPresentationFlow() {
        receiveJob?.cancel()
        discardPresentationPreview(activePresentationPreviewHandle())
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(presentationRequestUrl = ""),
                presentationReview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                presentationCompleted = false,
                pendingPresentationContinuation = null,
                presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
                operation = WalletOperationState.Idle,
            )
        }
    }

    fun previewOffer() {
        val current = _state.value
        val offerUrl = current.requestDrafts.offerUrl.trim()
        if (!current.receiveActionEnabled || offerUrl.isBlank()) return
        val request = ReceiveRequest(offerUrl, current.receiveNavigationResetKey)
        if (!_state.compareAndSet(current, current.copy(operation = WalletOperationState.ResolvingOffer))) return

        receiveJob = scope.launch(dispatcher) {
            try {
                val resolution = wallet.resolveOffer(offerUrl)
                currentCoroutineContext().ensureActive()
                if (!isCurrent(request)) {
                    wallet.discardIssuancePreview(resolution.previewHandle)
                    return@launch
                }
                current.offerPreview
                    ?.takeUnless { current.receiveCompleted }
                    ?.previewHandle
                    ?.let { previousHandle ->
                        wallet.discardIssuancePreview(previousHandle)
                    }
                updateIfCurrent(request) {
                    it.copy(
                        offerPreview = resolution,
                        operation = WalletOperationState.OfferPreview,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updateIfCurrent(request) {
                    it.copy(
                        operation = WalletOperationState.Failed(
                            message = WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, error),
                            tab = WalletDemoTab.Receive,
                        )
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
        if (!_state.compareAndSet(current, current.copy(operation = WalletOperationState.Receiving))) return

        receiveJob = scope.launch(dispatcher) {
            try {
                receiveCredential(ready, request, preview.previewHandle, txCode)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updateIfCurrent(request) {
                    it.copy(
                        operation = WalletOperationState.Failed(
                            message = WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, error),
                            tab = WalletDemoTab.Receive,
                        )
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
            updateIfCurrent(request) {
                it.copy(
                    session = ready.copy(credentials = credentials),
                    operation = WalletOperationState.Failed(
                        message = WalletDisplayText.failure(
                            WalletDisplayText.ReceiveFailed,
                            WalletDisplayText.ReceivedCredentialsUnavailable,
                        ),
                        tab = WalletDemoTab.Receive,
                    ),
                    offerPreview = null,
                    lastReceivedCredentialIds = emptyList(),
                    receiveCompleted = false,
                )
            }
            return
        }

        updateIfCurrent(request) {
            it.copy(
                session = ready.copy(credentials = credentials),
                offerPreview = null,
                operation = WalletOperationState.Succeeded(
                    message = WalletDisplayText.receivedCredentials(displayableReceivedCredentialIds.size),
                    tab = WalletDemoTab.Receive,
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
        transform: (WalletDemoUiState) -> WalletDemoUiState,
    ) {
        _state.update {
            if (
                it.receiveNavigationResetKey == request.navigationResetKey &&
                it.requestDrafts.offerUrl.trim() == request.offerUrl
            ) {
                transform(it)
            } else {
                it
            }
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
        val navigationResetKey = current.presentationNavigationResetKey
        val previousHandle = current.presentationPreview
            ?.takeUnless { current.presentationCompleted }
            ?.previewHandle
        if (requestUrl.isBlank()) return

        scope.launch(dispatcher) {
            _state.update {
                it.copy(
                    operation = WalletOperationState.ResolvingPresentation,
                    presentationReview = null,
                    selectedPresentationCredentialOptions = emptySet(),
                    selectedPresentationDisclosureOptions = emptySet(),
                    presentationCompleted = false,
                    pendingPresentationContinuation = null,
                )
            }
            runCatching {
                wallet.previewPresentation(requestUrl)
            }.onSuccess { result ->
                val latest = _state.value
                if (
                    latest.presentationNavigationResetKey != navigationResetKey ||
                    latest.requestDrafts.presentationRequestUrl.trim() != requestUrl
                ) {
                    wallet.discardPresentationPreview(result.previewHandle())
                    return@onSuccess
                }
                previousHandle?.let { wallet.discardPresentationPreview(it) }
                _state.update { state ->
                    state.copy(
                        operation = WalletOperationState.Idle,
                        presentationReview = result,
                        selectedPresentationCredentialOptions = when (result) {
                            is WalletDemoPresentationPreviewResult.Ready -> result.preview.defaultCredentialSelection()
                            is WalletDemoPresentationPreviewResult.Invalid -> emptySet()
                        },
                        selectedPresentationDisclosureOptions = emptySet(),
                    )
                }
            }.onFailure { error ->
                setOperationError(WalletDisplayText.PreviewFailed, error, WalletDemoTab.Present)
            }
        }
    }

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
        val requestUrl = current.requestDrafts.presentationRequestUrl.trim()
        val previewHandle = current.presentationPreview?.previewHandle ?: return
        val selectedCredentialOptions = current.selectedPresentationCredentialOptions.toList()
        val selectedDisclosureOptions = current.selectedPresentationDisclosureOptions
            .forSelectedCredentials(current.selectedPresentationCredentialOptions)
            .toList()
        if (requestUrl.isBlank()) return
        if (!current.presentationCredentialSelectionComplete()) {
            _state.update {
                it.copy(
                    operation = WalletOperationState.Failed(
                        WalletDisplayText.failure(
                            WalletDisplayText.PresentFailed,
                            WalletDisplayText.SelectCredentialForEveryRequest,
                        ),
                        WalletDemoTab.Present,
                    )
                )
            }
            return
        }

        scope.launch(dispatcher) {
            _state.update { it.copy(operation = WalletOperationState.Presenting) }
            runCatching {
                wallet.submitPresentation(previewHandle, selectedCredentialOptions, selectedDisclosureOptions, ready.did)
            }.onSuccess { result ->
                _state.update {
                    it.withPresentationResult(
                        result = result,
                        clearPreview = true,
                        clearSelections = true,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        presentationPreview = null,
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = false,
                    )
                }
                setOperationError(WalletDisplayText.PresentFailed, error, WalletDemoTab.Present)
            }
        }
    }

    private fun WalletDemoUiState.withPresentationResult(
        result: WalletDemoOperationResult,
        clearPreview: Boolean = false,
        clearSelections: Boolean = false,
        resetNavigation: Boolean = false,
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
        val previewHandle = activePresentationPreviewHandle() ?: return
        _state.update {
            it.copy(
                operation = WalletOperationState.Succeeded(
                    message = WalletDisplayText.PresentationReviewCancelled,
                    tab = WalletDemoTab.Present,
                ),
                presentationPreview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                presentationCompleted = false,
                presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
            )
        }
        scope.launch(dispatcher) {
            runCatching { wallet.discardPresentationPreview(previewHandle) }
                .onFailure { error -> setOperationError(WalletDisplayText.PresentFailed, error, WalletDemoTab.Present) }
        }
    }

    fun rejectPresentation() {
        val current = _state.value
        current.session as? WalletSessionState.Ready ?: return
        val previewHandle = activePresentationPreviewHandle() ?: return
        val isReportingError = current.presentationReview is WalletDemoPresentationPreviewResult.Invalid
        scope.launch(dispatcher) {
            _state.update { it.copy(operation = WalletOperationState.DecliningPresentation) }
            runCatching { wallet.rejectPresentation(previewHandle) }
                .onSuccess { result ->
                    _state.update {
                        it.withPresentationResult(
                            result = if (isReportingError && result is WalletDemoOperationResult.Success) {
                                result.copy(message = WalletDisplayText.VerifierNotified)
                            } else {
                                result
                            },
                            clearPreview = true,
                            clearSelections = true,
                            resetNavigation = true,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            presentationPreview = null,
                            selectedPresentationCredentialOptions = emptySet(),
                            selectedPresentationDisclosureOptions = emptySet(),
                            presentationCompleted = false,
                        )
                    }
                    setOperationError(WalletDisplayText.RejectFailed, error, WalletDemoTab.Present)
                }
        }
    }

    private fun discardIssuancePreview(previewHandle: WalletDemoIssuancePreviewHandle?) {
        if (previewHandle == null) return
        scope.launch(dispatcher) {
            runCatching { wallet.discardIssuancePreview(previewHandle) }
        }
    }

    private fun activeIssuancePreviewHandle(): WalletDemoIssuancePreviewHandle? =
        _state.value.let { state ->
            state.offerPreview
                ?.takeUnless { state.receiveCompleted }
                ?.previewHandle
        }

    private fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle?) {
        if (previewHandle == null) return
        scope.launch(dispatcher) {
            runCatching { wallet.discardPresentationPreview(previewHandle) }
        }
    }

    private fun activePresentationPreviewHandle(): WalletDemoPresentationPreviewHandle? =
        _state.value.takeUnless { it.presentationCompleted }?.presentationReview?.previewHandle()

    private fun WalletDemoPresentationPreviewResult.previewHandle(): WalletDemoPresentationPreviewHandle =
        when (this) {
            is WalletDemoPresentationPreviewResult.Ready -> preview.previewHandle
            is WalletDemoPresentationPreviewResult.Invalid -> error.previewHandle
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
        if (_state.value.session is WalletSessionState.Ready ||
            _state.value.session is WalletSessionState.Bootstrapping
        ) {
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
                )
            )
        }
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
