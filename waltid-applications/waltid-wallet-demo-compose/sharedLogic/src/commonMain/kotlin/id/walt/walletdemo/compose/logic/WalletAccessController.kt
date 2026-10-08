package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Owns access attempts. The wallet host supplies its atomic state update and unlock callback. */
internal class WalletAccessController(
    private val store: DemoPinStore,
    private val biometrics: DemoBiometricAuthenticator,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val current: () -> WalletAccessState,
    private val update: ((WalletAccessState) -> WalletAccessState) -> WalletAccessState,
    private val beforeSetupSave: () -> Unit,
    private val onUnlocked: () -> Unit,
) {
    private var job: Job? = null

    fun updatePin(value: String, confirmation: Boolean = false) {
        val before = update { state ->
            if (state.isBusy) return@update state
            val entry = state.pinEntry ?: return@update state
            val next = when (entry) {
                is WalletAuthState.Setup -> when {
                    confirmation && entry.step == PinSetupStep.Confirm -> entry.copy(confirmation = value, error = null)
                    !confirmation && entry.step == PinSetupStep.Choose -> entry.copy(pin = value, confirmation = "", error = null)
                    else -> return@update state
                }
                is WalletAuthState.Login -> if (!confirmation) entry.copy(pin = value, error = null, biometricOutcome = null)
                    else return@update state
            }
            state.withEntry(next).copy(operation = WalletAccessOperation.Idle)
        }
        val oldEntry = before.pinEntry
        val accepted = current().pinEntry
        if (!before.isBusy && accepted != oldEntry && validPin(value)) submitPin()
    }

    fun clearPin() {
        update { state ->
            if (state.isBusy) return@update state
            val entry = state.pinEntry ?: return@update state
            val cleared = when (entry) {
                is WalletAuthState.Setup -> if (entry.step == PinSetupStep.Confirm) entry.copy(confirmation = "", error = null)
                    else entry.copy(pin = "", confirmation = "", error = null)
                is WalletAuthState.Login -> entry.copy(pin = "", error = null, biometricOutcome = null)
            }
            state.withEntry(cleared).copy(operation = WalletAccessOperation.Idle)
        }
    }

    fun back() {
        update { state ->
            if (state.isBusy) return@update state
            val setup = state.pinEntry as? WalletAuthState.Setup ?: return@update state
            if (setup.step == PinSetupStep.Confirm) state.withEntry(WalletAuthState.Setup()).copy(operation = WalletAccessOperation.Idle)
            else if (state.pinChange != null) state.copy(pinChange = WalletPinChange.Current(), operation = WalletAccessOperation.Idle)
            else state
        }
    }

    fun submitPin() {
        val state = current()
        if (state.isBusy) return
        when (val entry = state.pinEntry) {
            is WalletAuthState.Setup -> {
                if (!validPin(entry.pin)) { pinError(WalletDisplayText.PinMustContain4Digits); return }
                if (entry.step == PinSetupStep.Choose) {
                    update { if (it == state) it.withEntry(entry.copy(step = PinSetupStep.Confirm, confirmation = "", error = null)) else it }
                } else if (!validPin(entry.confirmation)) return
                else if (entry.pin != entry.confirmation) {
                    update { if (it == state) it.withEntry(entry.copy(confirmation = "", error = WalletDisplayText.PinConfirmationDoesNotMatch)) else it }
                } else savePin(state, entry.pin)
            }
            is WalletAuthState.Login -> {
                if (!validPin(entry.pin)) { pinError(WalletDisplayText.PinMustContain4Digits); return }
                checkPin(state, entry.pin)
            }
            null -> Unit
        }
    }

    fun unlockWithBiometrics(force: Boolean = false) {
        val state = current()
        val login = state.auth as? WalletAuthState.Login ?: return
        if (state.isBusy || (!force && login.biometricPromptConsumed) || !state.biometricEnabled || !biometrics.isAvailable()) return
        val expected = state.copy(auth = login.copy(pin = "", error = null, biometricPromptConsumed = true, biometricOutcome = null))
        if (update { if (it == state) expected.copy(operation = WalletAccessOperation.Biometrics) else it } != state) return
        val attempt = state.generation
        job = scope.launch(dispatcher) {
            val result = authenticate(WalletDisplayText.UnlockWithBiometrics)
            if (!isCurrent(attempt)) return@launch
            if (result == DemoBiometricResult.Succeeded) unlock()
            else update { it.copy(auth = (it.auth as WalletAuthState.Login).copy(biometricOutcome = result),
                operation = WalletAccessOperation.Idle, biometricAvailability = biometrics.availability()) }
        }
    }

    fun refreshBiometrics() {
        try {
            val enabled = !store.isBiometricSetupPending() && store.isBiometricUnlockEnabled()
            update { it.copy(biometricAvailability = biometrics.availability(), biometricKind = biometrics.kind, biometricEnabled = enabled) }
        } catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { update { it.copy(settingsNotice = WalletAccessNotice("Could not read wallet access settings. Try again.", WalletAccessNotice.Kind.Error)) } }
    }

    fun retryBiometricSetup() {
        val state = current()
        if (state.auth !is WalletAuthState.BiometricSetup || !claim(state, WalletAccessOperation.Biometrics)) return
        val attempt = state.generation
        job = scope.launch(dispatcher) { offerBiometrics(attempt) }
    }

    fun continueWithoutBiometrics() {
        if (current().auth is WalletAuthState.BiometricSetup && !current().isBusy) finishBiometricSetup(false)
    }

    fun startPinChange() {
        if (current().auth != WalletAuthState.Unlocked || current().isBusy) return
        cancelAttempt()
        update { it.copy(pinChange = WalletPinChange.Current(), operation = WalletAccessOperation.Idle, settingsNotice = null) }
    }

    fun cancelPinChange() {
        // An outgoing settings view must not cancel the new unlock session after Lock.
        if (current().auth != WalletAuthState.Unlocked) return
        // A verifier is derived before the single record write. Keep this brief commit unambiguous.
        if (current().operation == WalletAccessOperation.SavingPin) return
        cancelAttempt()
        update { it.copy(pinChange = null, operation = WalletAccessOperation.Idle) }
    }

    fun setBiometricUnlock(enabled: Boolean) {
        val state = current()
        if (state.auth != WalletAuthState.Unlocked || state.pinChange != null || state.isBusy) return
        if (!enabled) { saveBiometricPreference(false); return }
        if (!biometrics.isAvailable()) { settingsError("Biometric unlock is unavailable on this device."); refreshBiometrics(); return }
        if (!claim(state, WalletAccessOperation.Biometrics)) return
        val attempt = state.generation
        job = scope.launch(dispatcher) {
            val result = authenticate(WalletDisplayText.EnableBiometricUnlock)
            if (!isCurrent(attempt)) return@launch
            if (result == DemoBiometricResult.Succeeded) saveBiometricPreference(true)
            else update { it.copy(operation = WalletAccessOperation.Idle, biometricAvailability = biometrics.availability(),
                settingsNotice = WalletAccessNotice(result.fallbackMessage() ?: "Biometric unlock was not enabled. You can try again.", WalletAccessNotice.Kind.Error)) }
        }
    }

    fun cancelAttempt() { update { it.copy(generation = it.generation + 1) }; job?.cancel(); job = null }

    fun retryStorage() {
        if (current().auth is WalletAuthState.StorageUnavailable) update { initialState(store, biometrics) }
    }

    private fun checkPin(expected: WalletAccessState, pin: String) {
        if (!claim(expected, WalletAccessOperation.CheckingPin)) return
        val attempt = expected.generation
        job = scope.launch(dispatcher) {
            try {
                val matches = store.verifyPin(pin)
                if (!isCurrent(attempt)) return@launch
                if (!matches) pinError(WalletDisplayText.WrongPin, clear = true)
                else if (expected.pinChange != null) update { it.copy(pinChange = WalletPinChange.NewPin(), operation = WalletAccessOperation.Idle) }
                else if (store.isBiometricSetupPending()) update { it.copy(auth = WalletAuthState.BiometricSetup(), operation = WalletAccessOperation.Idle) }
                else unlock()
            } catch (cause: CancellationException) { throw cause }
            catch (_: Exception) { if (isCurrent(attempt)) retryPin("PIN could not be verified. Try again.") }
        }
    }

    private fun savePin(expected: WalletAccessState, pin: String) {
        if (!claim(expected, WalletAccessOperation.SavingPin)) return
        val attempt = expected.generation
        job = scope.launch(dispatcher) {
            try {
                val changing = expected.pinChange != null
                val offer = !changing && biometrics.isAvailable()
                if (!changing) { beforeSetupSave(); store.setBiometricSetupPending(offer) }
                currentCoroutineContext().ensureActive()
                store.setPin(pin)
                if (!isCurrent(attempt)) return@launch
                if (changing) {
                    update { it.copy(pinChange = null, operation = WalletAccessOperation.Idle,
                        settingsNotice = WalletAccessNotice("PIN changed", WalletAccessNotice.Kind.Success)) }
                } else {
                    update { it.copy(auth = WalletAuthState.BiometricSetup(), operation = if (offer) WalletAccessOperation.Biometrics else WalletAccessOperation.Idle) }
                    store.setBiometricUnlockEnabled(false)
                    if (offer) offerBiometrics(attempt) else finishBiometricSetup(false)
                }
            } catch (cause: CancellationException) { throw cause }
            catch (_: Exception) {
                if (!isCurrent(attempt)) return@launch
                if (current().auth is WalletAuthState.BiometricSetup) biometricSetupError()
                else retryPin("PIN could not be saved. Try again.")
            }
        }
    }

    private suspend fun offerBiometrics(attempt: Long) {
        val result = authenticate(WalletDisplayText.EnableBiometricUnlock)
        if (!isCurrent(attempt)) return
        if (result == DemoBiometricResult.Succeeded) finishBiometricSetup(true)
        else update { it.copy(auth = WalletAuthState.BiometricSetup(outcome = result), operation = WalletAccessOperation.Idle,
            biometricAvailability = biometrics.availability()) }
    }

    private fun finishBiometricSetup(enabled: Boolean) {
        try { store.setBiometricUnlockEnabled(enabled); store.setBiometricSetupPending(false); unlock() }
        catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { biometricSetupError() }
    }

    private fun biometricSetupError() {
        update { it.copy(auth = (it.auth as WalletAuthState.BiometricSetup).copy(error = "Could not save the biometric choice. Try again."),
            operation = WalletAccessOperation.Idle) }
    }

    private fun saveBiometricPreference(enabled: Boolean) {
        try {
            store.setBiometricUnlockEnabled(enabled)
            update { it.copy(biometricEnabled = enabled, operation = WalletAccessOperation.Idle,
                settingsNotice = WalletAccessNotice(if (enabled) "Biometric unlock enabled" else "Biometric unlock turned off", WalletAccessNotice.Kind.Success)) }
        } catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { settingsError("Could not save biometric unlock. Try again.") }
    }

    private fun settingsError(message: String) {
        update { it.copy(operation = WalletAccessOperation.Idle, settingsNotice = WalletAccessNotice(message, WalletAccessNotice.Kind.Error)) }
    }

    private fun retryPin(message: String) { update { it.copy(operation = WalletAccessOperation.RetryPin(message)) } }

    private fun pinError(message: String, clear: Boolean = false) {
        update { state ->
            val entry = state.pinEntry ?: return@update state
            state.withEntry(when (entry) {
                is WalletAuthState.Login -> entry.copy(pin = if (clear) "" else entry.pin, error = message)
                is WalletAuthState.Setup -> entry.copy(error = message)
            }).copy(operation = WalletAccessOperation.Idle)
        }
    }

    private fun unlock() {
        update { it.copy(auth = WalletAuthState.Unlocked, operation = WalletAccessOperation.Idle,
            biometricEnabled = !store.isBiometricSetupPending() && store.isBiometricUnlockEnabled()) }
        onUnlocked()
    }

    private fun claim(expected: WalletAccessState, operation: WalletAccessOperation): Boolean =
        !expected.isBusy && update { if (it == expected) it.copy(operation = operation) else it } == expected

    private suspend fun isCurrent(attempt: Long): Boolean {
        currentCoroutineContext().ensureActive()
        return current().generation == attempt
    }

    private suspend fun authenticate(reason: String): DemoBiometricResult = try { biometrics.authenticate(reason) }
        catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { DemoBiometricResult.Failed }

    private fun WalletAccessState.withEntry(entry: WalletAuthState.PinEntry): WalletAccessState = when (pinChange) {
        is WalletPinChange.Current -> { val login = entry as WalletAuthState.Login; copy(pinChange = WalletPinChange.Current(login.pin, login.error)) }
        is WalletPinChange.NewPin -> copy(pinChange = WalletPinChange.NewPin(entry as WalletAuthState.Setup))
        null -> copy(auth = entry)
    }

    companion object {
        const val PinLength = 4
        fun validPin(pin: String): Boolean = pin.length == PinLength && pin.all { it in '0'..'9' }
        fun initialState(store: DemoPinStore, biometrics: DemoBiometricAuthenticator, skipPin: Boolean = false): WalletAccessState {
            if (skipPin) return WalletAccessState(auth = WalletAuthState.Unlocked)
            return try { WalletAccessState(auth = if (store.hasPin()) WalletAuthState.Login() else WalletAuthState.Setup(),
                biometricAvailability = biometrics.availability(), biometricKind = biometrics.kind,
                biometricEnabled = !store.isBiometricSetupPending() && store.isBiometricUnlockEnabled()) }
            catch (cause: CancellationException) { throw cause }
            catch (_: Exception) { WalletAccessState(auth = WalletAuthState.StorageUnavailable()) }
        }
    }
}
