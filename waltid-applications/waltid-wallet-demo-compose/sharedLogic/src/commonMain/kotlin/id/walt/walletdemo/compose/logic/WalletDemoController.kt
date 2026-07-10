package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        _state.update {
            it.copy(
                auth = WalletAuthState.Login(),
                operation = WalletOperationState.Idle,
            )
        }
    }

    fun selectTab(tab: WalletDemoTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun updateOfferUrl(value: String) {
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(offerUrl = value),
                lastReceivedCredentialIds = emptyList(),
                receiveCompleted = false,
            )
        }
    }

    fun updatePresentationRequestUrl(value: String) {
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(presentationRequestUrl = value),
                presentationPreview = null,
                selectedPresentationCredentialIds = emptySet(),
                presentationCompleted = false,
            )
        }
    }

    fun handleDeepLink(url: String) {
        when (WalletDeepLinkScheme.parse(url)) {
            WalletDeepLinkScheme.CredentialOffer -> {
                _state.update {
                    it.copy(
                        selectedTab = WalletDemoTab.Receive,
                        requestDrafts = it.requestDrafts.copy(offerUrl = url),
                        lastReceivedCredentialIds = emptyList(),
                        receiveCompleted = false,
                        receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
                        presentationPreview = null,
                        selectedPresentationCredentialIds = emptySet(),
                        presentationCompleted = false,
                        operation = WalletOperationState.Idle,
                    )
                }
            }
            WalletDeepLinkScheme.PresentationRequest -> {
                _state.update {
                    it.copy(
                        selectedTab = WalletDemoTab.Present,
                        requestDrafts = it.requestDrafts.copy(presentationRequestUrl = url),
                        presentationPreview = null,
                        selectedPresentationCredentialIds = emptySet(),
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
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(offerUrl = ""),
                lastReceivedCredentialIds = emptyList(),
                receiveCompleted = false,
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
                operation = WalletOperationState.Idle,
            )
        }
    }

    fun startNewPresentationFlow() {
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(presentationRequestUrl = ""),
                presentationPreview = null,
                selectedPresentationCredentialIds = emptySet(),
                presentationCompleted = false,
                presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
                operation = WalletOperationState.Idle,
            )
        }
    }

    fun receive() {
        val current = _state.value
        val ready = current.session as? WalletSessionState.Ready ?: return
        val offerUrl = current.requestDrafts.offerUrl.trim()
        if (offerUrl.isBlank()) return

        scope.launch(dispatcher) {
            _state.update { it.copy(operation = WalletOperationState.Receiving) }
            runCatching {
                val ids = wallet.receive(offerUrl)
                val credentials = wallet.listCredentials()
                ids to credentials
            }.onSuccess { (ids, credentials) ->
                val receivedCredentialIds = resolvedReceivedCredentialIds(
                    returnedCredentialIds = ids,
                    previousCredentials = ready.credentials,
                    refreshedCredentials = credentials,
                )
                val displayableReceivedCredentialIds = receivedCredentialIds
                    .filter { receivedCredentialId -> credentials.any { it.id == receivedCredentialId } }
                if (displayableReceivedCredentialIds.isEmpty()) {
                    _state.update {
                        it.copy(
                            session = ready.copy(credentials = credentials),
                            operation = WalletOperationState.Failed(
                                message = WalletDisplayText.failure(
                                    WalletDisplayText.ReceiveFailed,
                                    WalletDisplayText.ReceivedCredentialsUnavailable,
                                ),
                                tab = WalletDemoTab.Receive,
                            ),
                            lastReceivedCredentialIds = emptyList(),
                            receiveCompleted = false,
                        )
                    }
                    return@onSuccess
                }

                _state.update {
                    it.copy(
                        session = ready.copy(credentials = credentials),
                        operation = WalletOperationState.Succeeded(
                            message = WalletDisplayText.receivedCredentials(displayableReceivedCredentialIds.size),
                            tab = WalletDemoTab.Receive,
                        ),
                        lastReceivedCredentialIds = displayableReceivedCredentialIds,
                        receiveCompleted = true,
                    )
                }
            }.onFailure { error ->
                setOperationError(WalletDisplayText.ReceiveFailed, error, WalletDemoTab.Receive)
            }
        }
    }

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
                    selectedPresentationCredentialIds = emptySet(),
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
                        selectedPresentationCredentialIds = preview.credentialOptions
                            .map { option -> option.credentialId }
                            .toSet(),
                    )
                }
            }.onFailure { error ->
                setOperationError(WalletDisplayText.PreviewFailed, error, WalletDemoTab.Present)
            }
        }
    }

    fun togglePresentationCredential(credentialId: String) {
        _state.update { state ->
            val selected = state.selectedPresentationCredentialIds
            state.copy(
                selectedPresentationCredentialIds = if (credentialId in selected) {
                    selected - credentialId
                } else {
                    selected + credentialId
                }
            )
        }
    }

    fun submitPresentation() {
        val current = _state.value
        val ready = current.session as? WalletSessionState.Ready ?: return
        val requestUrl = current.requestDrafts.presentationRequestUrl.trim()
        val selectedCredentialIds = current.selectedPresentationCredentialIds.toList()
        if (requestUrl.isBlank() || selectedCredentialIds.isEmpty()) return

        scope.launch(dispatcher) {
            _state.update { it.copy(operation = WalletOperationState.Presenting) }
            runCatching {
                wallet.submitPresentation(requestUrl, selectedCredentialIds, ready.did)
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
                        selectedPresentationCredentialIds = emptySet(),
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
                selectedPresentationCredentialIds = emptySet(),
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
