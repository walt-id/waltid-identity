package id.walt.walletdemo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.walt.crypto.keys.KeyType
import id.walt.wallet2.client.EnterpriseWalletServiceClient
import id.walt.wallet2.client.WalletClient
import id.walt.wallet2.client.WalletClientAttestationProvider
import id.walt.wallet2.client.WalletClientAttestationStatus
import id.walt.wallet2.client.WalletClientEnvironment
import id.walt.wallet2.client.WalletClientEnvironmentProfile
import id.walt.wallet2.client.WalletCredentialSummary
import id.walt.wallet2.client.WalletEndpointRewriter
import id.walt.wallet2.client.toEnvironment
import id.walt.walletdemo.app.features.walletsdk.InMemoryWalletSdkAdapter
import id.walt.walletdemo.app.features.walletsdk.WalletErrorCategory
import id.walt.walletdemo.app.features.walletsdk.WalletErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class OperationStatus(
    val isLoading: Boolean = false,
    val message: String = "Ready",
    val category: WalletErrorCategory? = null,
    val isError: Boolean = false,
)

data class WalletUiState(
    val isBootstrapped: Boolean = false,
    val keyId: String = "",
    val did: String = "",
    val credentials: List<WalletCredentialSummary> = emptyList(),
    val status: OperationStatus = OperationStatus(),
    val attestationStatus: String = "Not checked",
    val enterpriseStatus: String = "",
    val environment: WalletClientEnvironment = WalletClientEnvironmentProfile.QuickstartLocal.toEnvironment(),
    val profile: WalletClientEnvironmentProfile = WalletClientEnvironmentProfile.QuickstartLocal,
    val requireAttestation: Boolean = false,
    val expirationSeconds: String = "",
)

class WalletViewModel : ViewModel() {

    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    private val rawAdapter = InMemoryWalletSdkAdapter()

    private fun buildClient(): WalletClient {
        val env = _state.value.environment
        val enterpriseClient = EnterpriseWalletServiceClient(environment = env)
        val attestationProvider = WalletClientAttestationProvider {
            check(env.enterpriseBaseUrl.isNotBlank() && env.walletPath.isNotBlank()) {
                "Enterprise URL/path required"
            }
            val attestation = enterpriseClient.getCurrentAttestation()
            _state.update { it.copy(attestationStatus = "Present (expiresAt=${attestation.expiresAt})") }
            WalletClientAttestationStatus.Present(attestation.expiresAt)
        }
        return WalletClient(
            adapter = rawAdapter,
            endpointRewriter = WalletEndpointRewriter.androidEmulatorLocalhost(),
            attestationProvider = attestationProvider,
        )
    }

    private fun enterpriseClient(): EnterpriseWalletServiceClient =
        EnterpriseWalletServiceClient(environment = _state.value.environment)

    fun updateEnvironment(env: WalletClientEnvironment) {
        _state.update { it.copy(environment = env, profile = WalletClientEnvironmentProfile.Custom) }
    }

    fun applyProfile(profile: WalletClientEnvironmentProfile) {
        if (profile == WalletClientEnvironmentProfile.Custom) {
            _state.update { it.copy(profile = profile) }
            return
        }
        _state.update { it.copy(profile = profile, environment = profile.toEnvironment()) }
    }

    fun deriveRefsFromWalletPath() {
        _state.update {
            val derived = WalletClientEnvironment(walletPath = it.environment.walletPath).withDerivedReferences()
            it.copy(
                environment = it.environment.copy(
                    attesterServiceRef = derived.attesterServiceRef,
                    instanceKeyReference = derived.instanceKeyReference,
                )
            )
        }
    }

    fun setRequireAttestation(value: Boolean) {
        _state.update { it.copy(requireAttestation = value) }
    }

    fun setExpirationSeconds(value: String) {
        _state.update { it.copy(expirationSeconds = value) }
    }

    fun bootstrap() {
        viewModelScope.launch {
            _state.update { it.copy(status = OperationStatus(isLoading = true, message = "Bootstrapping wallet...")) }
            runCatching {
                buildClient().bootstrapWallet(keyType = KeyType.secp256r1, didMethod = "key")
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        isBootstrapped = true,
                        keyId = result.keyId,
                        did = result.did,
                        status = OperationStatus(message = "Wallet ready"),
                    )
                }
                refreshCredentials()
            }.onFailure { error ->
                setError("bootstrap", error)
            }
        }
    }

    fun receiveCredential(offerUrl: String, txCode: String?) {
        viewModelScope.launch {
            _state.update { it.copy(status = OperationStatus(isLoading = true, message = "Receiving credential...")) }
            runCatching {
                buildClient().receiveCredential(
                    offerUrl = offerUrl.trim(),
                    txCode = txCode?.ifBlank { null },
                    requireAttestation = _state.value.requireAttestation,
                )
            }.onSuccess { ids ->
                _state.update {
                    it.copy(status = OperationStatus(message = "Received ${ids.size} credential(s)"))
                }
                refreshCredentials()
            }.onFailure { error ->
                setError("receive", error)
            }
        }
    }

    fun enterpriseReceive(offerUrl: String) {
        viewModelScope.launch {
            val env = _state.value.environment
            _state.update { it.copy(status = OperationStatus(isLoading = true, message = "Receiving via enterprise...")) }
            runCatching {
                enterpriseClient().receivePreAuthorized(
                    offerUrl = offerUrl.trim(),
                    keyReference = env.instanceKeyReference.trim(),
                )
            }.onSuccess { response ->
                val count = when (response) {
                    is JsonArray -> response.size
                    is JsonObject -> 1
                    else -> 0
                }
                _state.update {
                    it.copy(
                        status = OperationStatus(message = "Enterprise received $count credential(s)"),
                        enterpriseStatus = "Receive successful ($count)",
                    )
                }
            }.onFailure { error ->
                setError("enterprise-receive", error)
            }
        }
    }

    fun presentCredential(requestUrl: String) {
        viewModelScope.launch {
            _state.update { it.copy(status = OperationStatus(isLoading = true, message = "Presenting credential...")) }
            runCatching {
                buildClient().presentCredential(requestUrl = requestUrl.trim())
            }.onSuccess { result ->
                _state.update {
                    it.copy(status = OperationStatus(message = "Presented successfully"))
                }
            }.onFailure { error ->
                setError("present", error)
            }
        }
    }

    fun enterprisePresent(requestUrl: String) {
        viewModelScope.launch {
            val env = _state.value.environment
            _state.update { it.copy(status = OperationStatus(isLoading = true, message = "Presenting via enterprise...")) }
            runCatching {
                enterpriseClient().present(
                    requestUrl = requestUrl.trim(),
                    keyReference = env.instanceKeyReference.trim(),
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        status = OperationStatus(message = "Enterprise present successful"),
                        enterpriseStatus = "Present successful",
                    )
                }
            }.onFailure { error ->
                setError("enterprise-present", error)
            }
        }
    }

    fun obtainAttestation() {
        viewModelScope.launch {
            val env = _state.value.environment
            _state.update { it.copy(status = OperationStatus(isLoading = true, message = "Obtaining attestation...")) }
            runCatching {
                val request = buildJsonObject {
                    put("clientAttesterServiceRef", env.attesterServiceRef.trim())
                    put("instanceKeyReference", env.instanceKeyReference.trim())
                    _state.value.expirationSeconds.toLongOrNull()?.let { put("expirationSeconds", it) }
                }
                enterpriseClient().obtainAttestation(request)
            }.onSuccess { response ->
                _state.update {
                    it.copy(
                        attestationStatus = "Obtained (expiresAt=${response.expiresAt})",
                        status = OperationStatus(message = "Attestation obtained"),
                    )
                }
            }.onFailure { error ->
                setError("attestation-obtain", error)
            }
        }
    }

    fun checkAttestation() {
        viewModelScope.launch {
            _state.update { it.copy(status = OperationStatus(isLoading = true, message = "Checking attestation...")) }
            runCatching {
                enterpriseClient().getCurrentAttestation()
            }.onSuccess { att ->
                _state.update {
                    it.copy(
                        attestationStatus = "Present (expiresAt=${att.expiresAt})",
                        status = OperationStatus(message = "Attestation valid"),
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(attestationStatus = "Not available") }
                setError("attestation-check", error)
            }
        }
    }

    fun refreshCredentials() {
        viewModelScope.launch {
            runCatching { buildClient().listCredentials() }
                .onSuccess { list -> _state.update { it.copy(credentials = list) } }
                .onFailure { error -> setError("list", error) }
        }
    }

    private fun setError(operation: String, error: Throwable) {
        val mapped = WalletErrorMapper.map(operation, error)
        _state.update {
            it.copy(
                status = OperationStatus(
                    message = "${mapped.operation}: ${mapped.detail}",
                    category = mapped.category,
                    isError = true,
                )
            )
        }
    }
}
