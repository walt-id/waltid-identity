package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class WalletDemoController(
    private val wallet: DemoWallet,
    private val pinStore: DemoPinStore,
    private val biometricAuthenticator: DemoBiometricAuthenticator = UnavailableDemoBiometricAuthenticator,
    private val signingProtectionMode: WalletDemoSigningProtectionMode = WalletDemoSigningProtectionMode.Optional,
    private val signingProtectionStore: WalletDemoSigningProtectionStore =
        InMemoryWalletDemoSigningProtectionStore(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var receiveJob: Job? = null
    private var issuanceSession: WalletDemoIssuanceSession? = null
    private var presentationJob: Job? = null
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<WalletDemoUiState> = _state.asStateFlow()
    private var statusHideJob: Job? = null
    private var biometricSigningAvailabilityJob: Job? = null
    private var foregroundSequence = 0L
    private var lastWarnedForegroundSequence: Long? = null

    private fun initialState(): WalletDemoUiState = WalletDemoUiState(
        auth = readInitialAuthState(),
        biometricUnlockAvailable = biometricAuthenticator.isAvailable(),
        signingProtectionMode = signingProtectionMode,
        selectedSigningProtection = signingProtectionMode.resolve(signingProtectionStore.load()),
    )

    init {
        scope.launch(dispatcher) {
            state
                .map { ui ->
                    ui.statusBanner()
                        ?.takeIf { banner -> banner.kind == WalletStatusKind.Success && ui.isStatusVisible }
                        ?.key
                }
                .distinctUntilChanged()
                .collect { key ->
                    statusHideJob?.cancel()
                    if (key == null) return@collect
                    statusHideJob = launch {
                        delay(SuccessBannerAutoHide)
                        dismissStatus(key)
                    }
                }
        }
    }

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

    fun updateUseBiometrics(enabled: Boolean) {
        if (_state.value.auth !is WalletAuthState.Setup) return
        if (!enabled) {
            _state.update { state ->
                val current = state.auth as? WalletAuthState.Setup ?: return@update state
                state.copy(auth = current.copy(useBiometrics = false, error = null))
            }
            return
        }
        if (_state.value.isAuthenticating) return
        if (!biometricAuthenticator.isAvailable()) {
            _state.update { state ->
                val current = state.auth as? WalletAuthState.Setup ?: return@update state
                state.copy(
                    auth = current.copy(
                        useBiometrics = false,
                        error = WalletDisplayText.BiometricUnlockNotAuthorized,
                    ),
                )
            }
            return
        }
        _state.update { state ->
            val current = state.auth as? WalletAuthState.Setup ?: return@update state
            state.copy(
                auth = current.copy(useBiometrics = true, error = null),
                isAuthenticating = true,
            )
        }
        scope.launch(dispatcher) {
            val result = biometricAuthenticator.authenticate(WalletDisplayText.EnableBiometricUnlock)
            _state.update { state ->
                val current = state.auth as? WalletAuthState.Setup ?: return@update state.copy(isAuthenticating = false)
                if (result == DemoBiometricResult.Succeeded) {
                    state.copy(
                        auth = current.copy(useBiometrics = true, error = null),
                        isAuthenticating = false,
                    )
                } else {
                    state.copy(
                        auth = current.copy(
                            useBiometrics = false,
                            error = WalletDisplayText.BiometricUnlockNotAuthorized,
                        ),
                        isAuthenticating = false,
                    )
                }
            }
        }
    }

    fun isBiometricUnlockAvailable(): Boolean = biometricAuthenticator.isAvailable()

    fun isBiometricUnlockEnabled(): Boolean = pinStore.isBiometricUnlockEnabled()

    fun refreshBiometricUnlockAvailability() {
        _state.update { it.copy(biometricUnlockAvailable = biometricAuthenticator.isAvailable()) }
    }

    fun handleApplicationForegrounded() {
        refreshBiometricUnlockAvailability()
        foregroundSequence += 1
        refreshBiometricSigningAvailability(foregroundSequence)
        unlockWithBiometrics()
    }

    fun dismissSigningProtectionWarning() {
        _state.update { it.copy(signingProtectionWarning = null) }
    }

    fun unlockWithBiometrics(force: Boolean = false) {
        val auth = _state.value.auth as? WalletAuthState.Login ?: return
        if ((!force && auth.biometricPromptConsumed) || _state.value.isAuthenticating) return
        if (!pinStore.isBiometricUnlockEnabled() || !biometricAuthenticator.isAvailable()) return

        _state.update { state ->
            val login = state.auth as? WalletAuthState.Login ?: return@update state
            state.copy(
                auth = login.copy(biometricPromptConsumed = true),
                isAuthenticating = true,
            )
        }
        scope.launch(dispatcher) {
            when (biometricAuthenticator.authenticate(WalletDisplayText.UnlockWithBiometrics)) {
                DemoBiometricResult.Succeeded -> {
                    _state.update {
                        it.copy(
                            auth = WalletAuthState.Unlocked,
                            isAuthenticating = false,
                        )
                    }
                    showBiometricSigningWarningIfNeeded(foregroundSequence.takeIf { it > 0 })
                    bootstrapIfNeeded()
                }
                DemoBiometricResult.Failed -> _state.update { it.copy(isAuthenticating = false) }
            }
        }
    }

    fun selectSigningProtection(protection: WalletDemoSigningProtection) {
        if (signingProtectionMode != WalletDemoSigningProtectionMode.Optional) return
        _state.update { state ->
            if (state.auth !is WalletAuthState.Setup ||
                state.isAuthenticating ||
                (protection == WalletDemoSigningProtection.Biometric &&
                    state.biometricSigningAvailability != WalletDemoSigningProtectionAvailability.Available)
            ) {
                state
            } else {
                state.copy(
                    selectedSigningProtection = protection,
                    signingProtectionError = null,
                )
            }
        }
    }

    fun requestSigningProtectionChange(protection: WalletDemoSigningProtection) {
        val current = _state.value
        if (!signingProtectionMode.allows(protection) || current.isBusy) return
        if (current.signingProtectionReprovisionTarget != null) {
            reprovisionWallet(
                target = protection,
                previousSelection = current.selectedSigningProtection,
                recovering = true,
            )
            return
        }
        val applied = (current.session as? WalletSessionState.Ready)?.signingProtection
        if (applied == protection) return
        val previousSelection = current.selectedSigningProtection

        _state.update {
            it.copy(
                selectedSigningProtection = protection,
                isChangingSigningProtection = true,
                pendingSigningProtectionChange = null,
                signingProtectionError = null,
            )
        }
        scope.launch(dispatcher) {
            runCatching { wallet.signingProtectionAvailability(protection) }
                .onSuccess { availability ->
                    if (availability != WalletDemoSigningProtectionAvailability.Available) {
                        _state.update {
                            it.copy(
                                isChangingSigningProtection = false,
                                selectedSigningProtection = previousSelection,
                                signingProtectionError = availability.displayMessage(),
                            )
                        }
                    } else if (_state.value.session is WalletSessionState.Ready) {
                        _state.update {
                            it.copy(
                                isChangingSigningProtection = false,
                                pendingSigningProtectionChange = protection,
                            )
                        }
                    } else {
                        runCatching { signingProtectionStore.save(protection) }
                            .onSuccess {
                                _state.update {
                                    it.copy(
                                        selectedSigningProtection = protection,
                                        isChangingSigningProtection = false,
                                        signingProtectionError = null,
                                    )
                                }
                                bootstrapIfNeeded()
                            }
                            .onFailure { error -> setSigningProtectionError(error, previousSelection) }
                    }
                }
                .onFailure { error -> setSigningProtectionError(error, previousSelection) }
        }
    }

    fun cancelSigningProtectionChange() {
        _state.update {
            it.copy(
                selectedSigningProtection =
                    (it.session as? WalletSessionState.Ready)?.signingProtection
                        ?: signingProtectionMode.resolve(signingProtectionStore.load()),
                pendingSigningProtectionChange = null,
                signingProtectionError = null,
            )
        }
    }

    fun confirmSigningProtectionChange() {
        val current = _state.value
        val target = current.pendingSigningProtectionChange ?: return
        if (current.isBusy) return
        val previousSelection = (current.session as? WalletSessionState.Ready)?.signingProtection
            ?: signingProtectionMode.resolve(signingProtectionStore.load())
        reprovisionWallet(target, previousSelection, recovering = false)
    }

    private fun reprovisionWallet(
        target: WalletDemoSigningProtection,
        previousSelection: WalletDemoSigningProtection,
        recovering: Boolean,
    ) {
        _state.update {
            it.copy(
                isChangingSigningProtection = true,
                pendingSigningProtectionChange = null,
                signingProtectionError = null,
            )
        }
        scope.launch(dispatcher) {
            var replacementStarted = recovering
            runCatching {
                val availability = wallet.signingProtectionAvailability(target)
                check(availability == WalletDemoSigningProtectionAvailability.Available) {
                    availability.displayMessage().orEmpty()
                }
                signingProtectionStore.save(target)
                cancelActiveWalletWork()
                replacementStarted = true
                wallet.deleteWallet()
                val result = wallet.bootstrap(target)
                check(result.signingProtection == target) {
                    "Reprovisioned wallet did not apply the selected signing protection"
                }
                val credentials = wallet.listCredentials()
                result to credentials
            }.onSuccess { (result, credentials) ->
                statusHideJob?.cancel()
                _state.update { state ->
                    state.walletReplacementState(
                        session = WalletSessionState.Ready(
                            did = result.did,
                            keyId = result.keyId,
                            publicJwk = result.publicJwk,
                            signingProtection = result.signingProtection,
                            credentials = credentials,
                        ),
                        selectedSigningProtection = target,
                    ).copy(warning = result.warning)
                }
            }.onFailure { error ->
                if (!replacementStarted) {
                    runCatching { signingProtectionStore.save(previousSelection) }
                }
                _state.update { state ->
                    val message = WalletDisplayText.failure(
                        WalletDisplayText.SigningProtectionChangeFailed,
                        error,
                    )
                    if (replacementStarted) {
                        state.walletReplacementState(
                            session = WalletSessionState.Failed(message),
                            selectedSigningProtection = target,
                            signingProtectionReprovisionTarget = target,
                        ).copy(signingProtectionError = message)
                    } else {
                        state.copy(
                            selectedSigningProtection = previousSelection,
                            pendingSigningProtectionChange = null,
                            isChangingSigningProtection = false,
                            signingProtectionError = message,
                        )
                    }
                }
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
                auth = WalletAuthState.Login(biometricPromptConsumed = true),
                operation = WalletOperationState.Idle,
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
        cancelIssuance()
        discardPresentationPreview(previous.activePresentationPreviewHandle())
    }

    fun dismissStatus(key: String? = _state.value.statusBanner()?.key) {
        val dismissedKey = key ?: return
        _state.update { state ->
            if (state.statusBanner()?.key != dismissedKey) {
                state
            } else {
                state.copy(statusDismissedKey = dismissedKey, statusExpanded = false)
            }
        }
    }

    fun toggleStatusExpanded() {
        _state.update { state ->
            if (state.statusBanner()?.kind != WalletStatusKind.Error || !state.isStatusVisible) {
                state
            } else {
                state.copy(statusExpanded = !state.statusExpanded)
            }
        }
    }

    fun deleteCredential(credentialId: String) {
        if (_state.value.session !is WalletSessionState.Ready) return
        scope.launch(dispatcher) {
            runCatching { wallet.deleteCredential(credentialId) }
                .onSuccess { removed ->
                    if (!removed) return@onSuccess
                    runCatching { wallet.listCredentials() }
                        .onSuccess { credentials ->
                            val previous = getAndUpdateState { state ->
                                val currentReady = state.session as? WalletSessionState.Ready ?: return@getAndUpdateState state
                                val hadReview = state.presentationReview != null
                                state.copy(
                                    session = currentReady.copy(credentials = credentials),
                                    presentationReview = null,
                                    selectedPresentationCredentialOptions = emptySet(),
                                    selectedPresentationDisclosureOptions = emptySet(),
                                    presentationCompleted = false,
                                    presentationNavigationResetKey =
                                        if (hadReview) state.presentationNavigationResetKey + 1 else state.presentationNavigationResetKey,
                                )
                            }
                            if (previous.presentationReview != null) {
                                discardPresentationPreview(previous.activePresentationPreviewHandle())
                            }
                        }
                }
                .onFailure { error ->
                    setOperationError(WalletDisplayText.DeleteCredentialFailed, error, _state.value.selectedTab)
                }
        }
    }

    fun resetWallet() {
        cancelActiveWalletWork()
        scope.launch(dispatcher) {
            val deleted = runCatching { wallet.deleteWallet() }
            deleted.exceptionOrNull()?.let { error ->
                setOperationError(WalletDisplayText.ResetWalletFailed, error, _state.value.selectedTab)
                return@launch
            }
            val pinCleared = runCatching { pinStore.clear() }
            statusHideJob?.cancel()
            _state.value = initialState()
            refreshBiometricSigningAvailability()
            pinCleared.exceptionOrNull()?.let { error ->
                val message = WalletDisplayText.failure(WalletDisplayText.ResetWalletFailed, error)
                _state.update {
                    it.copy(
                        auth = WalletAuthState.Setup(error = message),
                        operation = WalletOperationState.Failed(message, WalletDemoTab.Credentials),
                    ).withPublishedStatus()
                }
            }
        }
    }

    fun selectTab(tab: WalletDemoTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    /**
     * Reloads credentials from the store into the ready session.
     *
     * Needed after a CREATE_CREDENTIAL provider activity (or another process-local wallet handle)
     * writes credentials while this controller's in-memory list is still stale.
     */
    fun refreshCredentialsFromStore() {
        scope.launch(dispatcher) {
            if (_state.value.session !is WalletSessionState.Ready) return@launch
            runCatching { wallet.listCredentials() }
                .onSuccess { credentials ->
                    _state.update { state ->
                        val currentReady = state.session as? WalletSessionState.Ready ?: return@update state
                        state.copy(session = currentReady.copy(credentials = credentials))
                    }
                }
        }
    }

    fun completePresentationContinuation() {
        _state.update { state ->
            val pending = state.pendingPresentationContinuation ?: return@update state
            state.withPublishedStatus().copy(
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
            state.withPublishedStatus().copy(
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
        getAndUpdateState {
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
        cancelIssuance()
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
        when (WalletDeepLinkScheme.parse(url)) {
            WalletDeepLinkScheme.CredentialOffer -> {
                receiveJob?.cancel()
                presentationJob?.cancel()
                val previous = getAndUpdateState {
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
                        presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
                        operation = WalletOperationState.Idle,
                    )
                }
                cancelIssuance()
                discardPresentationPreview(previous.activePresentationPreviewHandle())
            }
            WalletDeepLinkScheme.PresentationRequest -> {
                receiveJob?.cancel()
                presentationJob?.cancel()
                val previous = getAndUpdateState {
                    it.copy(
                        selectedTab = WalletDemoTab.Present,
                        requestDrafts = it.requestDrafts.copy(
                            presentationRequestUrl = url,
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
                        presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
                        operation = WalletOperationState.Idle,
                    )
                }
                cancelIssuance()
                discardPresentationPreview(previous.activePresentationPreviewHandle())
            }
            WalletDeepLinkScheme.AuthorizationCallback -> continueAuthorization(url)
            null -> Unit
        }
    }

    fun startNewReceiveFlow() {
        receiveJob?.cancel()
        getAndUpdateState {
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
        cancelIssuance()
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
            )
        }
        discardPresentationPreview(previous.activePresentationPreviewHandle())
    }

    fun previewOffer() {
        val current = _state.value
        val offerUrl = current.requestDrafts.offerUrl.trim()
        if (!current.receiveActionEnabled || offerUrl.isBlank()) return
        val request = ReceiveRequest(offerUrl, current.receiveNavigationResetKey)
        if (!_state.compareAndSet(current, current.copy(operation = WalletOperationState.ResolvingOffer))) return

        receiveJob = scope.launch(dispatcher) {
            var startedSession: WalletDemoIssuanceSession? = null
            try {
                val session = wallet.startIssuance(
                    offerUrl = offerUrl,
                    redirectUri = "openid://",
                    did = (current.session as? WalletSessionState.Ready)?.did,
                )
                startedSession = session
                currentCoroutineContext().ensureActive()
                val installed = updateIfCurrent(request, WalletOperationState.ResolvingOffer) {
                    it.copy(
                        offerPreview = session.preview,
                        operation = WalletOperationState.OfferPreview,
                    )
                }
                if (installed) {
                    issuanceSession = session
                } else {
                    wallet.cancelIssuance(session.id)
                }
                startedSession = null
            } catch (cancellation: CancellationException) {
                startedSession?.let { session ->
                    withContext(NonCancellable) { runCatching { wallet.cancelIssuance(session.id) } }
                }
                throw cancellation
            } catch (error: Throwable) {
                startedSession?.let { session ->
                    withContext(NonCancellable) { runCatching { wallet.cancelIssuance(session.id) } }
                }
                updateIfCurrent(request, WalletOperationState.ResolvingOffer) {
                    it.withFailedOperation(
                        WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, error),
                        WalletDemoTab.Receive,
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
        // Only forward a tx_code when the offer actually requested one; issuers now reject
        // an unsolicited tx_code (OpenID4VCI 1.0 §6.3).
        val txCode = current.requestDrafts.txCode.trim().ifBlank { null }
            ?.takeIf { preview.transactionCode != null }
        val request = ReceiveRequest(offerUrl, current.receiveNavigationResetKey)
        if (!_state.compareAndSet(current, current.copy(operation = WalletOperationState.Receiving))) return

        receiveJob = scope.launch(dispatcher) {
            try {
                val session = issuanceSession ?: error("Issuance session is missing")
                when (session.grant) {
                    WalletDemoIssuanceGrant.PreAuthorizedCode -> completeIssuanceOutcome(
                        ready, request, wallet.continuePreAuthorizedIssuance(session.id, txCode),
                    )
                    WalletDemoIssuanceGrant.AuthorizationCode -> {
                        val authorization = wallet.beginAuthorizationIssuance(session.id)
                        updateIfCurrent(request, WalletOperationState.Receiving) {
                            it.copy(authorizationRequestUrl = authorization.url, operation = WalletOperationState.OfferPreview)
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updateIfCurrent(request, WalletOperationState.Receiving) {
                    it.withFailedOperation(
                        WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, error),
                        WalletDemoTab.Receive,
                    )
                }
            }
        }
    }

    fun declineOffer() {
        val sessionId = issuanceSession?.id ?: return
        issuanceSession = null
        _state.update {
            it.copy(
                offerPreview = null, authorizationRequestUrl = null,
                requestDrafts = it.requestDrafts.copy(txCode = ""),
                operation = WalletOperationState.Succeeded(
                    message = WalletDisplayText.CredentialOfferDeclined,
                    tab = WalletDemoTab.Receive,
                ),
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
            ).withPublishedStatus()
        }
        scope.launch(dispatcher) {
            runCatching { wallet.cancelIssuance(sessionId) }
                .onFailure { error -> setOperationError(WalletDisplayText.ReceiveFailed, error, WalletDemoTab.Receive) }
        }
    }

    private suspend fun completeIssuanceOutcome(
        ready: WalletSessionState.Ready,
        request: ReceiveRequest,
        outcome: WalletDemoIssuanceOutcome,
    ) {
        val ids = when (outcome) {
            is WalletDemoIssuanceOutcome.Stored -> outcome.credentialIds
            is WalletDemoIssuanceOutcome.Deferred -> {
                issuanceSession = null
                updateIfCurrent(request, WalletOperationState.Receiving) {
                    it.copy(
                        offerPreview = null,
                        authorizationRequestUrl = null,
                        deferredCredentials = (it.deferredCredentials + outcome.credentials)
                            .distinctBy(WalletDemoDeferredCredential::id),
                        lastReceivedCredentialIds = outcome.storedCredentialIds,
                        receiveCompleted = outcome.storedCredentialIds.isNotEmpty(),
                        operation = WalletOperationState.Succeeded(
                            "Credential issuance deferred",
                            WalletDemoTab.Receive,
                        ),
                    ).withPublishedStatus()
                }
                return
            }
            WalletDemoIssuanceOutcome.Cancelled -> emptyList()
            is WalletDemoIssuanceOutcome.Failed -> error(outcome.message)
        }
        issuanceSession = null
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
                    offerPreview = null,
                    authorizationRequestUrl = null,
                    lastReceivedCredentialIds = emptyList(),
                    receiveCompleted = false,
                    operation = WalletOperationState.Failed(
                        WalletDisplayText.failure(
                            WalletDisplayText.ReceiveFailed,
                            WalletDisplayText.ReceivedCredentialsUnavailable,
                        ),
                        WalletDemoTab.Receive,
                    ),
                ).withPublishedStatus()
            }
            return
        }
        updateIfCurrent(request, WalletOperationState.Receiving) {
            it.copy(
                session = ready.copy(credentials = credentials),
                offerPreview = null,
                authorizationRequestUrl = null,
                requestDrafts = it.requestDrafts.copy(offerUrl = "", txCode = ""),
                operation = WalletOperationState.Succeeded(
                    WalletDisplayText.receivedCredentials(displayableReceivedCredentialIds.size),
                    WalletDemoTab.Credentials,
                ),
                lastReceivedCredentialIds = displayableReceivedCredentialIds,
                receiveCompleted = false,
                receiveNavigationResetKey = it.receiveNavigationResetKey + 1,
                selectedTab = WalletDemoTab.Credentials,
            ).withPublishedStatus()
        }
    }

    fun resumeDeferredCredential(deferredCredentialId: String) {
        val current = _state.value
        val ready = current.session as? WalletSessionState.Ready ?: return
        if (current.deferredCredentials.none { it.id == deferredCredentialId }) return
        if (!_state.compareAndSet(current, current.copy(operation = WalletOperationState.Receiving))) return

        receiveJob = scope.launch(dispatcher) {
            try {
                when (val outcome = wallet.resumeDeferredIssuance(deferredCredentialId)) {
                    is WalletDemoIssuanceOutcome.Stored -> {
                        val credentials = wallet.listCredentials()
                        _state.update {
                            val remainingDeferred = it.deferredCredentials.filterNot { pending -> pending.id == deferredCredentialId }
                            val received = outcome.credentialIds.isNotEmpty() && remainingDeferred.isEmpty()
                            it.copy(
                                session = ready.copy(credentials = credentials),
                                deferredCredentials = remainingDeferred,
                                lastReceivedCredentialIds = outcome.credentialIds,
                                receiveCompleted = false,
                                offerPreview = if (received) null else it.offerPreview,
                                requestDrafts = if (received) {
                                    it.requestDrafts.copy(offerUrl = "", txCode = "")
                                } else {
                                    it.requestDrafts
                                },
                                receiveNavigationResetKey = if (received) {
                                    it.receiveNavigationResetKey + 1
                                } else {
                                    it.receiveNavigationResetKey
                                },
                                selectedTab = if (received) WalletDemoTab.Credentials else it.selectedTab,
                                operation = WalletOperationState.Succeeded(
                                    WalletDisplayText.receivedCredentials(outcome.credentialIds.size),
                                    if (received) WalletDemoTab.Credentials else WalletDemoTab.Receive,
                                ),
                            ).withPublishedStatus()
                        }
                    }
                    is WalletDemoIssuanceOutcome.Deferred -> _state.update {
                        it.copy(
                            deferredCredentials = (
                                it.deferredCredentials.filterNot { pending -> pending.id == deferredCredentialId } + outcome.credentials
                                ).distinctBy(WalletDemoDeferredCredential::id),
                            operation = WalletOperationState.Succeeded("Credential issuance still pending", WalletDemoTab.Receive),
                        ).withPublishedStatus()
                    }
                    WalletDemoIssuanceOutcome.Cancelled -> _state.update {
                        it.copy(
                            deferredCredentials = it.deferredCredentials.filterNot { it.id == deferredCredentialId },
                            operation = WalletOperationState.Succeeded(
                                WalletDisplayText.CredentialOfferDeclined,
                                WalletDemoTab.Receive,
                            ),
                        ).withPublishedStatus()
                    }
                    is WalletDemoIssuanceOutcome.Failed -> _state.update {
                        it.copy(
                            operation = WalletOperationState.Failed(
                                WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, outcome.message),
                                WalletDemoTab.Receive,
                            ),
                        ).withPublishedStatus()
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                setOperationError(WalletDisplayText.ReceiveFailed, error, WalletDemoTab.Receive)
            }
        }
    }

    fun authorizationRequestOpened() { _state.update { it.copy(authorizationRequestUrl = null) } }

    private fun continueAuthorization(callbackUri: String) {
        val session = issuanceSession ?: return
        val current = _state.value
        val ready = current.session as? WalletSessionState.Ready ?: return
        val request = ReceiveRequest(current.requestDrafts.offerUrl.trim(), current.receiveNavigationResetKey)
        if (!_state.compareAndSet(current, current.copy(operation = WalletOperationState.Receiving))) return
        receiveJob = scope.launch(dispatcher) {
            try {
                completeIssuanceOutcome(
                    ready,
                    request,
                    wallet.continueAuthorizationIssuance(session.id, callbackUri),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updateIfCurrent(request, WalletOperationState.Receiving) {
                    it.withFailedOperation(
                        WalletDisplayText.failure(WalletDisplayText.ReceiveFailed, error),
                        WalletDemoTab.Receive,
                    )
                }
            }
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
                    it.withFailedOperation(
                        WalletDisplayText.failure(WalletDisplayText.PreviewFailed, error),
                        WalletDemoTab.Present,
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
                current.withFailedOperation(
                    WalletDisplayText.failure(
                        WalletDisplayText.PresentFailed,
                        WalletDisplayText.SelectCredentialForEveryRequest,
                    ),
                    WalletDemoTab.Present,
                ),
            )
            return
        }
        val request = PresentationRequest(requestUrl, current.presentationNavigationResetKey)
        if (!_state.compareAndSet(current, current.copy(operation = WalletOperationState.Presenting))) return

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
                    it.withFailedOperation(
                        WalletDisplayText.failure(WalletDisplayText.PresentFailed, error),
                        WalletDemoTab.Present,
                    ).copy(
                        presentationReview = null,
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = false,
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
    ): WalletDemoUiState {
        val success = result as? WalletDemoOperationResult.Success
        val pending = success?.continuation?.let { continuation ->
            WalletDemoPendingPresentationContinuation(
                continuation = continuation,
                successMessage = success.message,
            )
        }

        return copy(
            statusOccurrenceId = statusOccurrenceId + 1,
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
        val current = _state.value
        if (!current.presentationReviewEnabled) return
        val previewHandle = current.activePresentationPreviewHandle() ?: return
        if (!_state.compareAndSet(
                current,
                current.withPublishedStatus().copy(
                    operation = WalletOperationState.Succeeded(
                        message = WalletDisplayText.PresentationReviewCancelled,
                        tab = WalletDemoTab.Present,
                    ),
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
        if (!_state.compareAndSet(current, current.copy(operation = WalletOperationState.DecliningPresentation))) return

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
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updatePresentationIfCurrent(request, WalletOperationState.DecliningPresentation) {
                    it.withFailedOperation(
                        WalletDisplayText.failure(WalletDisplayText.RejectFailed, error),
                        WalletDemoTab.Present,
                    ).copy(
                        presentationReview = null,
                        selectedPresentationCredentialOptions = emptySet(),
                        selectedPresentationDisclosureOptions = emptySet(),
                        presentationCompleted = false,
                        presentationNavigationResetKey = it.presentationNavigationResetKey + 1,
                    )
                }
            }
        }
    }

    private fun cancelIssuance() {
        val sessionId = issuanceSession?.id ?: return
        issuanceSession = null
        scope.launch(dispatcher) {
            runCatching { wallet.cancelIssuance(sessionId) }
        }
    }

    private fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle?) {
        if (previewHandle == null) return
        scope.launch(dispatcher) {
            runCatching { wallet.discardPresentationPreview(previewHandle) }
        }
    }

    private fun cancelActiveWalletWork() {
        receiveJob?.cancel()
        presentationJob?.cancel()
        val previous = _state.value
        cancelIssuance()
        discardPresentationPreview(previous.activePresentationPreviewHandle())
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
            val protection = _state.value.selectedSigningProtection
            runCatching {
                val availability = wallet.signingProtectionAvailability(protection)
                check(availability == WalletDemoSigningProtectionAvailability.Available) {
                    availability.displayMessage().orEmpty()
                }
                signingProtectionStore.save(protection)
                pinStore.setPin(pin)
                pinStore.setBiometricUnlockEnabled(auth.useBiometrics)
            }
                .onSuccess {
                    _state.update {
                        it.copy(
                            auth = WalletAuthState.Unlocked,
                            isAuthenticating = false,
                        )
                    }
                    showBiometricSigningWarningIfNeeded(foregroundSequence.takeIf { it > 0 })
                    bootstrapIfNeeded()
                }
                .onFailure { error ->
                    setSetupPinError(error.message ?: "PIN could not be saved")
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
                    showBiometricSigningWarningIfNeeded(foregroundSequence.takeIf { it > 0 })
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
                val result = wallet.bootstrap(_state.value.selectedSigningProtection)
                val credentials = wallet.listCredentials()
                val selection = signingProtectionMode.resolve(result.signingProtection)
                signingProtectionStore.save(selection)
                Triple(result, credentials, selection)
            }.onSuccess { (result, credentials, selection) ->
                _state.update {
                    it.copy(
                        session = WalletSessionState.Ready(
                            did = result.did,
                            keyId = result.keyId,
                            publicJwk = result.publicJwk,
                            signingProtection = result.signingProtection,
                            credentials = credentials,
                        ),
                        operation = WalletOperationState.Idle,
                        warning = result.warning,
                        selectedSigningProtection = selection,
                        signingProtectionReprovisionTarget = null,
                    ).withPublishedStatus()
                }
                showBiometricSigningWarningIfNeeded(foregroundSequence.takeIf { it > 0 })
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        session = WalletSessionState.Failed(WalletDisplayText.failure(WalletDisplayText.BootstrapFailed, error)),
                        operation = WalletOperationState.Idle,
                    ).withPublishedStatus()
                }
            }
        }
    }

    private fun refreshBiometricSigningAvailability(warningSequence: Long? = null) {
        biometricSigningAvailabilityJob?.cancel()
        biometricSigningAvailabilityJob = scope.launch(dispatcher) {
            val availability = try {
                wallet.signingProtectionAvailability(WalletDemoSigningProtection.Biometric)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                WalletDemoSigningProtectionAvailability.Unsupported
            }
            _state.update {
                it.copy(
                    biometricSigningAvailability = availability,
                    signingProtectionWarning = if (availability == WalletDemoSigningProtectionAvailability.Available) {
                        null
                    } else {
                        it.signingProtectionWarning
                    },
                )
            }
            showBiometricSigningWarningIfNeeded(warningSequence)
        }
    }

    private fun showBiometricSigningWarningIfNeeded(warningSequence: Long?) {
        val sequence = warningSequence ?: return
        if (lastWarnedForegroundSequence == sequence) return
        val state = _state.value
        val applied = (state.session as? WalletSessionState.Ready)?.signingProtection
        val availability = state.biometricSigningAvailability ?: return
        if (state.auth != WalletAuthState.Unlocked ||
            applied != WalletDemoSigningProtection.Biometric ||
            availability == WalletDemoSigningProtectionAvailability.Available
        ) {
            return
        }
        lastWarnedForegroundSequence = sequence
        _state.update {
            it.copy(
                signingProtectionWarning = WalletDisplayText.biometricSigningUnavailable(
                    availability = availability,
                    canChooseNoBiometricSigning = signingProtectionMode.allows(WalletDemoSigningProtection.None),
                ),
            )
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

    private fun setSigningProtectionError(
        error: Throwable,
        previousSelection: WalletDemoSigningProtection,
    ) {
        _state.update {
            it.copy(
                selectedSigningProtection = previousSelection,
                isChangingSigningProtection = false,
                signingProtectionError = WalletDisplayText.failure(
                    WalletDisplayText.SigningProtectionChangeFailed,
                    error,
                ),
            )
        }
    }

    private fun WalletDemoUiState.walletReplacementState(
        session: WalletSessionState,
        selectedSigningProtection: WalletDemoSigningProtection,
        signingProtectionReprovisionTarget: WalletDemoSigningProtection? = null,
    ): WalletDemoUiState = copy(
        session = session,
        selectedSigningProtection = selectedSigningProtection,
        pendingSigningProtectionChange = null,
        signingProtectionReprovisionTarget = signingProtectionReprovisionTarget,
        isChangingSigningProtection = false,
        signingProtectionError = null,
        operation = WalletOperationState.Idle,
        requestDrafts = WalletRequestDrafts(),
        offerPreview = null,
        authorizationRequestUrl = null,
        deferredCredentials = emptyList(),
        lastReceivedCredentialIds = emptyList(),
        receiveCompleted = false,
        receiveNavigationResetKey = receiveNavigationResetKey + 1,
        presentationReview = null,
        selectedPresentationCredentialOptions = emptySet(),
        selectedPresentationDisclosureOptions = emptySet(),
        presentationCompleted = false,
        presentationNavigationResetKey = presentationNavigationResetKey + 1,
        warning = null,
        signingProtectionWarning = null,
        pendingPresentationContinuation = null,
        statusDismissedKey = null,
        statusExpanded = false,
    ).withPublishedStatus()

    private fun setOperationError(prefix: String, error: Throwable, tab: WalletDemoTab) {
        _state.update {
            it.withFailedOperation(WalletDisplayText.failure(prefix, error), tab)
        }
    }

    private fun WalletDemoUiState.withFailedOperation(message: String, tab: WalletDemoTab): WalletDemoUiState =
        withPublishedStatus().copy(operation = WalletOperationState.Failed(message = message, tab = tab))

    private fun WalletDemoUiState.withPublishedStatus(): WalletDemoUiState =
        copy(statusOccurrenceId = statusOccurrenceId + 1)

    private fun readInitialAuthState(): WalletAuthState =
        runCatching {
            if (pinStore.hasPin()) WalletAuthState.Login() else WalletAuthState.Setup()
        }.getOrElse {
            WalletAuthState.StorageUnavailable()
        }

    private companion object {
        val pinPattern = Regex("\\d{4,8}")
        val SuccessBannerAutoHide = 4.seconds

        fun isValidPin(pin: String): Boolean = pin.matches(pinPattern)
    }
}
