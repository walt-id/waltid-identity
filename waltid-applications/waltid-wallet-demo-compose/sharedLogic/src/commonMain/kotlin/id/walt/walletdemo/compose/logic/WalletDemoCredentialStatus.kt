package id.walt.walletdemo.compose.logic

import kotlinx.serialization.Serializable

/** Renderer input. LOADING/server capability are host-only; issuer semantics come from the shared producer. */
@Serializable
data class WalletDemoCredentialStatus(
    val kind: String,
    val issue: String,
    val freshness: String,
    val action: String,
    val advisoryRevision: String,
    val coverage: String = "NONE",
    val lastCheckedAt: String? = null,
    val lastAttemptAt: String? = null,
    val failure: String? = null,
    val purposes: List<String> = emptyList(),
    val references: List<WalletDemoStatusReference> = emptyList(),
    val lastKnown: Boolean = false,
    val discoveryProblem: String? = null,
    /** Store scope supplied by the snapshot adapter, never inferred from the display label. */
    val storeIndex: Int? = null,
) {
    val hasIssue: Boolean get() = issue != "NONE" || kind !in knownKinds ||
        coverage !in setOf("NONE", "COMPLETE", "PARTIAL") || action !in setOf("NONE", "CHECK", "RETRY") ||
        freshness !in setOf("UNKNOWN", "FRESH", "STALE")
    val adverse: Boolean get() = issue in setOf("REVOKED", "INVALID", "SUSPENDED")
    val red: Boolean get() = issue in setOf("REVOKED", "INVALID")
    val positive: Boolean get() = !hasIssue && freshness == "FRESH" && coverage == "COMPLETE" &&
        kind in setOf("NOT_REVOKED", "NOT_SUSPENDED", "CHECKED")

    companion object {
        private val knownKinds = setOf("NOT_PROVIDED", "NOT_CHECKED", "NOT_REVOKED", "NOT_SUSPENDED", "CHECKED",
            "REVOKED", "INVALID", "SUSPENDED", "OUT_OF_DATE", "UNAVAILABLE", "UNVERIFIED", "UNSUPPORTED",
            "PARTIAL", "NEEDS_REVIEW", "SERVER_UNSUPPORTED", "LOADING")
        val Ambiguous = WalletDemoCredentialStatus("NEEDS_REVIEW", "NEEDS_REVIEW", "UNKNOWN", "NONE", "", failure = "AMBIGUOUS_CREDENTIAL_ID")
        val ServerUnsupported = WalletDemoCredentialStatus("SERVER_UNSUPPORTED", "UNCERTAIN", "UNKNOWN", "NONE", "server-unsupported")
        val Unavailable = WalletDemoCredentialStatus("UNAVAILABLE", "UNCERTAIN", "UNKNOWN", "NONE", "")
        val Loading = WalletDemoCredentialStatus("LOADING", "NONE", "UNKNOWN", "NONE", "")
    }
}

@Serializable
data class WalletDemoStatusReference(
    val referenceId: String,
    val protocolName: String? = null,
    val method: String? = null,
    val purpose: String? = null,
    val result: String? = null,
    val checkedAt: String? = null,
    val freshUntil: String? = null,
    val validUntil: String? = null,
    val attemptedAt: String? = null,
    val failure: String? = null,
    val conflict: Boolean = false,
    val problem: String? = null,
    val eligibleAt: String? = null,
    val freshness: String = "UNKNOWN",
)

/** A pending local read is distinct from an unavailable completed read; neither is issuer status. */
enum class WalletDemoStatusReadState {
    Loading, Available, Unavailable;

    val awaitingRead: WalletDemoStatusReadState get() = if (this == Unavailable) Unavailable else Loading
}

data class WalletDemoCredentialStatuses(
    val credentials: Map<String, WalletDemoCredentialStatus> = emptyMap(),
    val refreshing: Set<String> = emptySet(),
    val nextRefreshAt: String? = null,
    val readState: WalletDemoStatusReadState = WalletDemoStatusReadState.Available,
    /** Only an explicitly detected older server may use the legacy submission contract. */
    val legacySubmission: Boolean = false,
) {
    val available: Boolean get() = readState == WalletDemoStatusReadState.Available
}

data class WalletDemoStatusRefreshSummary(val completed: Int, val unchanged: Int, val deferred: Int, val checkErrors: Int)

/** UI availability does not alter issuer evidence or manufacture a review acknowledgement. */
fun WalletDemoCredentialStatuses.displayStatus(credentialId: String): WalletDemoCredentialStatus {
    if (readState == WalletDemoStatusReadState.Loading) {
        val retained = credentials[credentialId] ?: return WalletDemoCredentialStatus.Loading
        return if (retained.hasIssue) retained.copy(lastKnown = retained.lastKnown || retained.adverse)
        else retained.copy(kind = "LOADING", issue = "NONE", freshness = "UNKNOWN")
    }
    val retained = credentials[credentialId] ?: return WalletDemoCredentialStatus.Unavailable
    if (available) return retained
    return if (retained.adverse) retained.copy(lastKnown = true)
    else retained.copy(kind = "UNAVAILABLE", issue = "UNCERTAIN", freshness = "UNKNOWN")
}

class WalletDemoStatusReviewChanged : IllegalStateException("Credential status changed. Review the updated information before sharing.")
