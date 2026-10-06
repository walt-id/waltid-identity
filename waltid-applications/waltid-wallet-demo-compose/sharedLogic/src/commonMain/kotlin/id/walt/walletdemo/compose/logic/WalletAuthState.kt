package id.walt.walletdemo.compose.logic

sealed interface WalletAuthState {
    sealed interface PinEntry : WalletAuthState

    data class Setup(
        val pin: String = "",
        val confirmation: String = "",
        val error: String? = null,
        val step: PinSetupStep = PinSetupStep.Choose,
    ) : PinEntry

    data class Login(
        val pin: String = "",
        val error: String? = null,
        val biometricPromptConsumed: Boolean = false,
    ) : PinEntry

    /** The PIN has been saved or verified; only the optional biometric choice remains. */
    data class BiometricSetup(
        val outcome: DemoBiometricResult? = null,
        val error: String? = null,
    ) : WalletAuthState

    data class StorageUnavailable(
        val message: String = "PIN storage is unavailable",
    ) : WalletAuthState

    data object Unlocked : WalletAuthState
}

enum class PinSetupStep { Choose, Confirm }
