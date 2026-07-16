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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var configuredPin: String? = null
    private var receiveJob: Job? = null
    private val _state = MutableStateFlow(WalletDemoUiState())
    val state: StateFlow<WalletDemoUiState> = _state.asStateFlow()

    fun updatePin(value: String) {
        _state.update { state ->
            when (val auth = state.auth) {
                is WalletAuthState.Setup -> state.copy(auth = auth.copy(pin = value, error = null))
                is WalletAuthState.Login -> state.copy(auth = auth.copy(pin = value, error = null))
                WalletAuthState.Unlocked -> state
            }
        }
    }

    fun updatePinConfirmation(value: String) {
        _state.update { state ->
            when (val auth = state.auth) {
                is WalletAuthState.Setup -> state.copy(auth = auth.copy(confirmation = value, error = null))
                is WalletAuthState.Login,
                WalletAuthState.Unlocked,
                -> state
            }
        }
    }

    fun submitPin() {
        when (val auth = _state.value.auth) {
            is WalletAuthState.Setup -> submitSetupPin(auth)
            is WalletAuthState.Login -> submitLoginPin(auth)
            WalletAuthState.Unlocked -> Unit
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

    fun updateOfferUrl(value: String) {
        receiveJob?.cancel()
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(
                    offerUrl = value,
                    txCode = "",
                    transactionCodeRequired = false,
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
            it.copy(requestDrafts = it.requestDrafts.copy(txCode = value))
        }
    }

    fun updatePresentationRequestUrl(value: String) {
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(presentationRequestUrl = value),
                presentationPreview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                presentationCompleted = false,
            )
        }
    }

    fun handleDeepLink(url: String) {
        when (WalletDeepLinkScheme.parse(url)) {
            WalletDeepLinkScheme.CredentialOffer -> {
                receiveJob?.cancel()
                _state.update {
                    it.copy(
                        selectedTab = WalletDemoTab.Receive,
                        requestDrafts = it.requestDrafts.copy(
                            offerUrl = url,
                            txCode = "",
                            transactionCodeRequired = false,
                        ),
                        offerPreview = null,
                        lastReceivedCredentialIds = emptyList(),
                        receiveCompleted = false,
                        receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
                        presentationPreview = null,
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = false,
                        operation = WalletOperationState.Idle,
                    )
                }
            }
            WalletDeepLinkScheme.PresentationRequest -> {
                receiveJob?.cancel()
                _state.update {
                    it.copy(
                        selectedTab = WalletDemoTab.Present,
                        requestDrafts = it.requestDrafts.copy(presentationRequestUrl = url),
                        presentationPreview = null,
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = false,
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
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(
                    offerUrl = "",
                    txCode = "",
                    transactionCodeRequired = false,
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
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(presentationRequestUrl = ""),
                presentationPreview = null,
                selectedPresentationCredentialOptions = emptySet(),
                selectedPresentationDisclosureOptions = emptySet(),
                presentationCompleted = false,
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
                if (!isCurrent(request)) return@launch
                updateIfCurrent(request) {
                    it.copy(
                        requestDrafts = it.requestDrafts.copy(
                            transactionCodeRequired = resolution.transactionCodeRequired,
                        ),
                        offerPreview = WalletDemoOfferPreview(
                            credentialIssuer = resolution.credentialIssuer,
                            offeredCredentials = resolution.offeredCredentials,
                            transactionCodeRequired = resolution.transactionCodeRequired,
                        ),
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
        val offerUrl = current.requestDrafts.offerUrl.trim()
        val txCode = current.requestDrafts.txCode.trim().ifBlank { null }
        val request = ReceiveRequest(offerUrl, current.receiveNavigationResetKey)
        if (!_state.compareAndSet(current, current.copy(operation = WalletOperationState.Receiving))) return

        receiveJob = scope.launch(dispatcher) {
            try {
                receiveCredential(ready, request, txCode)
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
        _state.update {
            it.copy(
                offerPreview = null,
                requestDrafts = it.requestDrafts.copy(
                    txCode = "",
                    transactionCodeRequired = false,
                ),
                operation = WalletOperationState.Succeeded(
                    message = WalletDisplayText.CredentialOfferDeclined,
                    tab = WalletDemoTab.Receive,
                ),
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
            )
        }
    }

    private suspend fun receiveCredential(
        ready: WalletSessionState.Ready,
        request: ReceiveRequest,
        txCode: String?,
    ) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(request)) return
        val ids = wallet.receive(request.offerUrl, txCode)
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
                _state.update {
                    it.copy(
                        operation = when (result) {
                            is WalletDemoOperationResult.Success -> WalletOperationState.Succeeded(
                                message = result.message,
                                tab = WalletDemoTab.Present,
                            )
                            is WalletDemoOperationResult.Failure -> WalletOperationState.Failed(
                                message = result.message,
                                tab = WalletDemoTab.Present,
                            )
                        },
                    )
                }
            }.onFailure { error ->
                setOperationError(WalletDisplayText.PresentFailed, error, WalletDemoTab.Present)
            }
        }
    }

    fun previewPresentation() {
        val current = _state.value
        current.session as? WalletSessionState.Ready ?: return
        val requestUrl = current.requestDrafts.presentationRequestUrl.trim()
        if (requestUrl.isBlank()) return

        scope.launch(dispatcher) {
            _state.update {
                it.copy(
                    operation = WalletOperationState.ResolvingPresentation,
                    presentationPreview = null,
                    selectedPresentationCredentialOptions = emptySet(),
                    selectedPresentationDisclosureOptions = emptySet(),
                    presentationCompleted = false,
                )
            }
            runCatching {
                wallet.previewPresentation(requestUrl)
            }.onSuccess { preview ->
                _state.update {
                    it.copy(
                        operation = WalletOperationState.Idle,
                        presentationPreview = preview,
                        selectedPresentationCredentialOptions = preview.defaultCredentialSelection(),
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
                wallet.submitPresentation(requestUrl, selectedCredentialOptions, selectedDisclosureOptions, ready.did)
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        operation = when (result) {
                            is WalletDemoOperationResult.Success -> WalletOperationState.Succeeded(
                                message = result.message,
                                tab = WalletDemoTab.Present,
                            )
                            is WalletDemoOperationResult.Failure -> WalletOperationState.Failed(
                                message = result.message,
                                tab = WalletDemoTab.Present,
                            )
                        },
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = result is WalletDemoOperationResult.Success,
                    )
                }
            }.onFailure { error ->
                setOperationError(WalletDisplayText.PresentFailed, error, WalletDemoTab.Present)
            }
        }
    }

    fun cancelPresentationReview() {
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

        configuredPin = pin
        _state.update { it.copy(auth = WalletAuthState.Unlocked) }
        bootstrapIfNeeded()
    }

    private fun submitLoginPin(auth: WalletAuthState.Login) {
        val pin = auth.pin
        if (!isValidPin(pin)) {
            setLoginPinError(WalletDisplayText.PinMustContain4To8Digits)
            return
        }

        if (configuredPin != pin) {
            setLoginPinError(WalletDisplayText.WrongPin)
            return
        }

        _state.update { it.copy(auth = WalletAuthState.Unlocked) }
        bootstrapIfNeeded()
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
            state.copy(auth = auth.copy(error = message))
        }
    }

    private fun setLoginPinError(message: String) {
        _state.update { state ->
            val auth = state.auth as? WalletAuthState.Login ?: return@update state
            state.copy(auth = auth.copy(error = message))
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

    private companion object {
        val pinPattern = Regex("\\d{4,8}")

        fun isValidPin(pin: String): Boolean = pin.matches(pinPattern)
    }
}
