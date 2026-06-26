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
        _state.update { it.copy(pin = value, pinError = null) }
    }

    fun updatePinConfirmation(value: String) {
        _state.update { it.copy(pinConfirmation = value, pinError = null) }
    }

    fun submitPin() {
        val current = _state.value
        val pin = current.pin

        if (!pin.matches(Regex("\\d{4,8}"))) {
            _state.update { it.copy(pinError = "PIN must contain 4 to 8 digits") }
            return
        }

        when (current.pinMode) {
            WalletDemoPinMode.Setup -> setupPin(pin, current.pinConfirmation)
            WalletDemoPinMode.Login -> unlockWithPin(pin)
        }
    }

    fun lock() {
        _state.update {
            it.copy(
                pinMode = WalletDemoPinMode.Login,
                isUnlocked = false,
                pin = "",
                pinConfirmation = "",
                pinError = null,
                status = "Enter PIN to unlock the wallet",
            )
        }
    }

    fun updateOfferUrl(value: String) {
        _state.update { it.copy(offerUrl = value) }
    }

    fun updatePresentationRequestUrl(value: String) {
        _state.update { it.copy(presentationRequestUrl = value) }
    }

    fun handleDeepLink(url: String) {
        when {
            url.startsWith("openid-credential-offer:") -> updateOfferUrl(url)
            url.startsWith("openid4vp:") -> updatePresentationRequestUrl(url)
        }
    }

    fun receive() {
        val offerUrl = _state.value.offerUrl.trim()
        if (offerUrl.isBlank()) return

        scope.launch(dispatcher) {
            _state.update { it.copy(isBusy = true, isError = false, status = "Receiving credential...") }
            runCatching {
                val ids = wallet.receive(offerUrl)
                val credentials = wallet.listCredentials()
                ids to credentials
            }.onSuccess { (ids, credentials) ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        isError = false,
                        status = "Received ${ids.size} credential(s)",
                        credentials = credentials,
                    )
                }
            }.onFailure { error ->
                setError("Receive failed", error)
            }
        }
    }

    fun present() {
        val requestUrl = _state.value.presentationRequestUrl.trim()
        if (requestUrl.isBlank()) return

        scope.launch(dispatcher) {
            _state.update { it.copy(isBusy = true, isError = false, status = "Presenting credential...") }
            runCatching {
                wallet.present(requestUrl, _state.value.did.takeIf { it.isNotBlank() })
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        isError = !result.success,
                        status = result.message,
                    )
                }
            }.onFailure { error ->
                setError("Present failed", error)
            }
        }
    }

    private fun setupPin(pin: String, confirmation: String) {
        if (pin != confirmation) {
            _state.update { it.copy(pinError = "PIN confirmation does not match") }
            return
        }

        configuredPin = pin
        _state.update {
            it.copy(
                pinMode = WalletDemoPinMode.Login,
                isUnlocked = true,
                pin = "",
                pinConfirmation = "",
                pinError = null,
            )
        }
        bootstrapIfNeeded()
    }

    private fun unlockWithPin(pin: String) {
        if (configuredPin != pin) {
            _state.update { it.copy(pinError = "Wrong PIN") }
            return
        }

        _state.update {
            it.copy(
                isUnlocked = true,
                pin = "",
                pinConfirmation = "",
                pinError = null,
            )
        }
        bootstrapIfNeeded()
    }

    private fun bootstrapIfNeeded() {
        if (_state.value.isReady) return

        scope.launch(dispatcher) {
            _state.update { it.copy(isBusy = true, isError = false, status = "Bootstrapping wallet...") }
            runCatching {
                val result = wallet.bootstrap()
                val credentials = wallet.listCredentials()
                result to credentials
            }.onSuccess { (result, credentials) ->
                _state.update {
                    it.copy(
                        isReady = true,
                        isBusy = false,
                        isError = false,
                        status = "Wallet ready",
                        did = result.did,
                        credentials = credentials,
                    )
                }
            }.onFailure { error ->
                setError("Bootstrap failed", error)
            }
        }
    }

    private fun setError(prefix: String, error: Throwable) {
        _state.update {
            it.copy(
                isBusy = false,
                isError = true,
                status = "$prefix: ${error.message ?: error::class.simpleName ?: "Unexpected error"}",
            )
        }
    }
}
