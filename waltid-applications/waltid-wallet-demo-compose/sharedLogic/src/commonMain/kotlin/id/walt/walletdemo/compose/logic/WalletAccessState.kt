package id.walt.walletdemo.compose.logic

/** Demo app access; independent from credential signing and key provisioning. */
data class WalletAccessState(
    val auth: WalletAuthState = WalletAuthState.Setup(),
    val operation: WalletAccessOperation = WalletAccessOperation.Idle,
    val biometricAvailability: DemoBiometricAvailability = DemoBiometricAvailability.Unavailable,
    val biometricEnabled: Boolean = false,
    val biometricKind: DemoBiometricKind = DemoBiometricKind.Generic,
    val pinChange: WalletPinChange? = null,
    val settingsNotice: WalletAccessNotice? = null,
    internal val generation: Long = 0,
) {
    val biometricAvailable: Boolean get() = biometricAvailability == DemoBiometricAvailability.Available
    val isBusy: Boolean get() = operation == WalletAccessOperation.CheckingPin ||
        operation == WalletAccessOperation.SavingPin || operation == WalletAccessOperation.Biometrics
    val pinEntry: WalletAuthState.PinEntry? get() = when (val change = pinChange) {
        is WalletPinChange.Current -> WalletAuthState.Login(change.pin, change.error, biometricPromptConsumed = true)
        is WalletPinChange.NewPin -> change.setup
        null -> auth as? WalletAuthState.PinEntry
    }
}

sealed interface WalletAccessOperation {
    data object Idle : WalletAccessOperation
    data object CheckingPin : WalletAccessOperation
    data object SavingPin : WalletAccessOperation
    data object Biometrics : WalletAccessOperation
    data class RetryPin(val message: String) : WalletAccessOperation
}

sealed interface WalletPinChange {
    data class Current(val pin: String = "", val error: String? = null) : WalletPinChange
    data class NewPin(val setup: WalletAuthState.Setup = WalletAuthState.Setup()) : WalletPinChange
}

data class WalletAccessNotice(val message: String, val kind: Kind) {
    enum class Kind { Success, Error }
}

enum class DemoBiometricKind { Generic, FaceId, TouchId }

fun DemoBiometricResult.fallbackMessage(): String? = when (this) {
    DemoBiometricResult.Succeeded, DemoBiometricResult.Cancelled -> null
    DemoBiometricResult.Unavailable -> "Biometric unlock is unavailable. Use your PIN."
    DemoBiometricResult.LockedOut -> "Biometrics are locked. Use your PIN."
    DemoBiometricResult.Failed -> "Biometric unlock failed. Use your PIN or try again."
}
