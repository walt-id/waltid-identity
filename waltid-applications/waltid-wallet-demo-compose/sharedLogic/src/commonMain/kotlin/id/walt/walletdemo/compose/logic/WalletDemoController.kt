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

    fun updateOfferUrl(value: String) {
        _state.update {
            it.copy(requestDrafts = it.requestDrafts.copy(offerUrl = value, txCodeRequired = false, offerFromDeepLink = false))
        }
    }

    fun clearOfferDeepLinkFlag() {
        _state.update {
            it.copy(requestDrafts = it.requestDrafts.copy(offerFromDeepLink = false))
        }
    }

    fun updateTxCode(value: String) {
        _state.update {
            it.copy(requestDrafts = it.requestDrafts.copy(txCode = value))
        }
    }

    fun loadCredentialDetails(id: String) {
        scope.launch(dispatcher) {
            val details = runCatching { wallet.credentialDetails(id) }.getOrNull()
            _state.update { it.copy(selectedCredentialDetails = details) }
        }
    }

    fun clearCredentialDetails() {
        _state.update { it.copy(selectedCredentialDetails = null) }
    }

    fun updatePresentationRequestUrl(value: String) {
        _state.update {
            it.copy(requestDrafts = it.requestDrafts.copy(presentationRequestUrl = value))
        }
    }

    fun handleDeepLink(url: String) {
        when {
            url.startsWith("openid-credential-offer:") -> {
                _state.update {
                    it.copy(requestDrafts = it.requestDrafts.copy(
                        offerUrl = url,
                        txCode = "",
                        txCodeRequired = false,
                        offerFromDeepLink = true,
                    ))
                }
                if (_state.value.session is WalletSessionState.Ready) {
                    resolveAndReceive(url)
                }
                // If not ready, bootstrapIfNeeded will call resolveAndReceive once bootstrap completes.
            }
            url.startsWith("openid4vp:") -> updatePresentationRequestUrl(url)
        }
    }

    /**
     * Resolves an offer URL and either proceeds directly to receive (no txCode required) or
     * pauses and waits for the user to supply a txCode before calling [receive].
     * Used by both QR scan and manual URL entry.
     */
    fun resolveAndReceive(offerUrl: String) {
        val ready = _state.value.session as? WalletSessionState.Ready ?: return
        val url = offerUrl.trim()
        if (url.isBlank()) return

        scope.launch(dispatcher) {
            _state.update {
                it.copy(
                    requestDrafts = it.requestDrafts.copy(offerUrl = url, txCode = "", txCodeRequired = false, offerFromDeepLink = false),
                    operation = WalletOperationState.ResolvingOffer,
                )
            }
            runCatching {
                wallet.resolveOffer(url)
            }.onSuccess { resolution ->
                if (resolution.txCodeRequired) {
                    _state.update {
                        it.copy(
                            requestDrafts = it.requestDrafts.copy(txCodeRequired = true),
                            operation = WalletOperationState.Idle,
                        )
                    }
                } else {
                    doReceive(ready, url, txCode = null)
                }
            }.onFailure { error ->
                setOperationError("Resolve failed", error)
            }
        }
    }

    fun receive() {
        val current = _state.value
        val ready = current.session as? WalletSessionState.Ready ?: return
        val offerUrl = current.requestDrafts.offerUrl.trim()
        if (offerUrl.isBlank()) return
        val txCode = current.requestDrafts.txCode.trim().ifBlank { null }

        scope.launch(dispatcher) {
            doReceive(ready, offerUrl, txCode)
        }
    }

    private suspend fun doReceive(ready: WalletSessionState.Ready, offerUrl: String, txCode: String?) {
        _state.update {
            it.copy(
                requestDrafts = it.requestDrafts.copy(txCodeRequired = false),
                operation = WalletOperationState.Receiving,
            )
        }
        runCatching {
            val ids = wallet.receive(offerUrl, txCode)
            val credentials = wallet.listCredentials()
            ids to credentials
        }.onSuccess { (ids, credentials) ->
            _state.update {
                it.copy(
                    session = ready.copy(credentials = credentials),
                    operation = WalletOperationState.Succeeded("Received ${ids.size} credential(s)"),
                )
            }
        }.onFailure { error ->
            setOperationError("Receive failed", error)
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
                            is WalletDemoOperationResult.Success -> WalletOperationState.Succeeded(result.message)
                            is WalletDemoOperationResult.Failure -> WalletOperationState.Failed(result.message)
                        },
                    )
                }
            }.onFailure { error ->
                setOperationError("Present failed", error)
            }
        }
    }

    private fun submitSetupPin(auth: WalletAuthState.Setup) {
        val pin = auth.pin
        if (!isValidPin(pin)) {
            setSetupPinError("PIN must contain 4 to 8 digits")
            return
        }

        if (pin != auth.confirmation) {
            setSetupPinError("PIN confirmation does not match")
            return
        }

        configuredPin = pin
        _state.update { it.copy(auth = WalletAuthState.Unlocked) }
        bootstrapIfNeeded()
    }

    private fun submitLoginPin(auth: WalletAuthState.Login) {
        val pin = auth.pin
        if (!isValidPin(pin)) {
            setLoginPinError("PIN must contain 4 to 8 digits")
            return
        }

        if (configuredPin != pin) {
            setLoginPinError("Wrong PIN")
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
                val pendingOffer = _state.value.requestDrafts
                    .takeIf { it.offerFromDeepLink }
                    ?.offerUrl
                    ?.takeIf { it.isNotBlank() }
                if (pendingOffer != null) resolveAndReceive(pendingOffer)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        session = WalletSessionState.Failed(errorMessage("Bootstrap failed", error)),
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

    private fun setOperationError(prefix: String, error: Throwable) {
        _state.update {
            it.copy(operation = WalletOperationState.Failed(errorMessage(prefix, error)))
        }
    }

    private fun errorMessage(prefix: String, error: Throwable): String =
        "$prefix: ${error.message ?: error::class.simpleName ?: "Unexpected error"}"

    private companion object {
        val pinPattern = Regex("\\d{4,8}")

        fun isValidPin(pin: String): Boolean = pin.matches(pinPattern)
    }
}
