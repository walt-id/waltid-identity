package id.walt.walletdemo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.walt.wallet2.client.MobileWalletClientFactory
import id.walt.wallet2.client.MobileWalletConfig
import id.walt.wallet2.client.NativeWalletCredential
import id.walt.wallet2.client.WalletAttestationConfig
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.HttpEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalletUiState(
    val isReady: Boolean = false,
    val isBusy: Boolean = false,
    val isError: Boolean = false,
    val status: String = "Starting wallet...",
    val did: String = "",
    val offerUrl: String = "",
    val presentationRequestUrl: String = "",
    val credentials: List<NativeWalletCredential> = emptyList(),
)

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val client = MobileWalletClientFactory(application).create(
        MobileWalletConfig(
            attestationConfig = BuildConfig.ATTESTATION_BASE_URL.takeIf { it.isNotBlank() }?.let {
                WalletAttestationConfig(
                    enterpriseBaseUrl = it,
                    attesterPath = BuildConfig.ATTESTATION_ATTESTER_PATH,
                    bearerToken = BuildConfig.ATTESTATION_BEARER_TOKEN,
                    enterpriseHostHeader = BuildConfig.ATTESTATION_HOST_HEADER,
                )
            }
        )
    )
    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    init {
        WebDataFetcherManager.globalDefaultConfiguration = WebDataFetchingConfiguration(http = HttpEngine.CIO)

        viewModelScope.launch {
            runCatching { client.bootstrap() }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isReady = true,
                            isError = false,
                            status = "Wallet ready",
                            did = result.did,
                        )
                    }
                }
                .onFailure { setError("Bootstrap failed", it) }
        }
    }

    fun setOfferUrl(value: String) {
        _state.update { it.copy(offerUrl = value) }
    }

    fun setPresentationRequestUrl(value: String) {
        _state.update { it.copy(presentationRequestUrl = value) }
    }

    fun receive() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, isError = false, status = "Receiving credential...") }
            runCatching { client.receive(_state.value.offerUrl) }
                .onSuccess { ids ->
                    val credentials = client.credentials()
                    _state.update {
                        it.copy(
                            isBusy = false,
                            isError = false,
                            status = "Received ${ids.size} credential(s)",
                            credentials = credentials,
                        )
                    }
                }
                .onFailure { setError("Receive failed", it) }
        }
    }

    fun present() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, isError = false, status = "Presenting credential...") }
            runCatching { client.present(_state.value.presentationRequestUrl) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            isError = false,
                            status = if (result.success) "Presentation sent" else "Presentation finished without verifier confirmation",
                        )
                    }
                }
                .onFailure { setError("Present failed", it) }
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
