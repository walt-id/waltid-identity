package id.walt.walletdemo.compose.logic

/** Recovery actions retain an SDK-owned handle in the mobile adapter. */
data class WalletDemoIdentityChoice(val id: String, val title: String, val detail: String, val recoverable: Boolean, val destructive: Boolean = false)

/** A display value for one decision, not an executable SDK configuration. */
data class WalletDemoKeyChoice(val id: String, val title: String, val detail: String, val identifier: String? = null)

/** A complete supported configuration. Only its opaque handle is submitted to the SDK. */
data class WalletDemoKeySetupOption(
    val id: String,
    val recovery: WalletDemoKeyChoice,
    val storage: WalletDemoKeyChoice,
    val approval: WalletDemoKeyChoice,
    val restoring: Boolean = false,
)

sealed interface WalletDemoIdentitySetup {
    data class Choose(
        val options: List<WalletDemoKeySetupOption>,
        val message: String? = null,
        val recoveryStorageNotice: String? = null,
        val recoveryUnavailableReasons: List<String> = emptyList(),
    ) : WalletDemoIdentitySetup
    data class Pending(val identityId: String, val explanation: String, val canRetry: Boolean) : WalletDemoIdentitySetup
}

/** Each step filters the SDK's complete options; the UI never constructs a combination. */
enum class WalletDemoKeySetupStep(val title: String) {
    Recovery("Recovery"), Storage("Key storage"), Approval("Signing approval");

    fun choice(option: WalletDemoKeySetupOption): WalletDemoKeyChoice = when (this) {
        Recovery -> option.recovery
        Storage -> option.storage
        Approval -> option.approval
    }

    fun options(all: List<WalletDemoKeySetupOption>, selected: WalletDemoKeySetupOption): List<WalletDemoKeySetupOption> =
        all.filter { option -> when (this) {
            Recovery -> true
            Storage -> option.recovery.id == selected.recovery.id
            Approval -> option.recovery.id == selected.recovery.id && option.storage.id == selected.storage.id
        } }

    fun select(all: List<WalletDemoKeySetupOption>, selected: WalletDemoKeySetupOption, choiceId: String): WalletDemoKeySetupOption {
        val candidates = options(all, selected).filter { choice(it).id == choiceId }
        return candidates.firstOrNull { it.storage.id == selected.storage.id && it.approval.id == selected.approval.id }
            ?: candidates.firstOrNull { it.approval.id == selected.approval.id }
            ?: candidates.firstOrNull() ?: selected
    }
}

/** Public facts and SDK-issued actions only; no recovery secret reaches UI state. */
data class WalletDemoIdentityDetails(val storage: String, val origin: String, val authorization: String,
    val recovery: String, val choices: List<WalletDemoIdentityChoice>,
    val protection: String = "Unknown", val providerFailures: List<String> = emptyList())

/** Loading and failure must not be mistaken for a wallet without identity management. */
sealed interface WalletDemoIdentityDetailsState {
    data object Loading : WalletDemoIdentityDetailsState
    data object Unsupported : WalletDemoIdentityDetailsState
    data class Available(val details: WalletDemoIdentityDetails) : WalletDemoIdentityDetailsState
    data class Failed(val message: String) : WalletDemoIdentityDetailsState
}

/** Only intentional, user-facing explanations may pass through this exception. */
internal class WalletDemoKeyOperationException(message: String) : IllegalStateException(message)

internal fun keyOperationFailure(cause: Throwable): String =
    (cause as? WalletDemoKeyOperationException)?.message
        ?: "Could not complete the signing-key operation. Check device and backup availability, then try again."
