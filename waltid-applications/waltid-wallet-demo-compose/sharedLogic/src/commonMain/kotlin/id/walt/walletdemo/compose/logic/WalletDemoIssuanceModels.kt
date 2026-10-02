package id.walt.walletdemo.compose.logic

enum class WalletDemoIssuanceGrant { PreAuthorizedCode, AuthorizationCode }

data class WalletDemoHolderBinding(val keyId: String, val did: String? = null) {
    init { require(keyId.isNotBlank()) }
}

sealed interface WalletDemoCredentialHolders {
    data class Existing(val bindings: List<WalletDemoHolderBinding>) : WalletDemoCredentialHolders
    data class NewKeys(val count: Int) : WalletDemoCredentialHolders
}

data class WalletDemoCredentialSelection(
    val credentialConfigurationId: String,
    val holders: WalletDemoCredentialHolders,
) {
    init {
        require(credentialConfigurationId.isNotBlank())
    }
}

data class WalletDemoIssuanceSession(
    val id: String,
    val grant: WalletDemoIssuanceGrant,
    val preview: WalletDemoOfferPreview,
)

data class WalletDemoIssuanceAuthorization(
    val url: String,
)

data class WalletDemoDeferredCredential(
    val id: String,
    val credentialConfigurationId: String? = null,
    val intervalSeconds: Long?,
    val credentialIdentifier: String? = null,
    val status: WalletDemoContinuationStatus = WalletDemoContinuationStatus.Unresolved,
    val displayMetadataJson: String? = null,
)

enum class WalletDemoContinuationStatus {
    Unresolved, AwaitingIssuer, AwaitingLocalSave, RemoteOutcomeUncertain, StorageOutcomeUncertain;

    val canResume: Boolean get() = this != RemoteOutcomeUncertain && this != StorageOutcomeUncertain
}

fun WalletDemoDeferredCredential.credentialDisplay(preferredLocales: List<String> = platformPreferredLocales()): WalletDemoMetadataDisplay? =
    StoredCredentialMetadataParser.credentialDisplay(displayMetadataJson, preferredLocales)

enum class WalletDemoIssuanceFailureKind { General, RemoteOutcomeUncertain, StorageOutcomeUncertain }

data class WalletDemoIssuanceProblem(
    val message: String,
    val kind: WalletDemoIssuanceFailureKind = WalletDemoIssuanceFailureKind.General,
    val failedTargetCount: Int = 0,
    val notAttemptedTargetCount: Int = 0,
)

/** The result belongs to this request; older retained work is not attributed to its issuer. */
data class WalletDemoIssuanceReceipt(
    val issuer: WalletDemoIssuerMetadata? = null,
    val pendingIds: Set<String> = emptySet(),
    val problem: WalletDemoIssuanceProblem? = null,
)

sealed interface WalletDemoIssuanceOutcome {
    data class Stored(val credentialIds: List<String>) : WalletDemoIssuanceOutcome
    data class Deferred(
        val storedCredentialIds: List<String>,
        val credentials: List<WalletDemoDeferredCredential> = emptyList(),
    ) : WalletDemoIssuanceOutcome
    data object Cancelled : WalletDemoIssuanceOutcome
    data class Failed(
        val message: String,
        val storedCredentialIds: List<String> = emptyList(),
        val deferredCredentials: List<WalletDemoDeferredCredential> = emptyList(),
        val offerConsumed: Boolean = false,
        val failedTargetCount: Int = 0,
        val notAttemptedTargetCount: Int = 0,
        val kind: WalletDemoIssuanceFailureKind = WalletDemoIssuanceFailureKind.General,
    ) : WalletDemoIssuanceOutcome
}

internal fun WalletDemoIssuanceOutcome.Failed.problem() =
    WalletDemoIssuanceProblem(message, kind, failedTargetCount, notAttemptedTargetCount)

internal fun WalletDemoIssuanceOutcome.withContinuations(latest: List<WalletDemoDeferredCredential>, resumingId: String? = null): WalletDemoIssuanceOutcome {
    val byId = latest.associateBy { it.id }
    fun updated(items: List<WalletDemoDeferredCredential>) = items.map { item ->
        val retained = byId[item.id]
        val uncertain = if (item.id == resumingId) when ((this as? WalletDemoIssuanceOutcome.Failed)?.kind) {
            WalletDemoIssuanceFailureKind.RemoteOutcomeUncertain -> WalletDemoContinuationStatus.RemoteOutcomeUncertain
            WalletDemoIssuanceFailureKind.StorageOutcomeUncertain -> WalletDemoContinuationStatus.StorageOutcomeUncertain
            else -> null
        } else null
        // Keep a newer interval returned by the operation; status/art come from the retained record.
        item.copy(status = retained?.status?.takeUnless { it == WalletDemoContinuationStatus.Unresolved } ?: uncertain ?: item.status,
            displayMetadataJson = retained?.displayMetadataJson ?: item.displayMetadataJson)
    }
    return when (this) {
        is WalletDemoIssuanceOutcome.Deferred -> copy(credentials = updated(credentials))
        is WalletDemoIssuanceOutcome.Failed -> copy(deferredCredentials = updated(deferredCredentials))
        else -> this
    }
}
