package id.walt.walletdemo.compose.logic

import kotlinx.serialization.Serializable

/** Renderer input. Semantic codes come from the SDK/server; UI never interprets status-list bits. */
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
) {
    val hasIssue: Boolean get() = issue != "NONE"
    val adverse: Boolean get() = issue in setOf("REVOKED", "INVALID", "SUSPENDED")
    val red: Boolean get() = issue in setOf("REVOKED", "INVALID")

    companion object {
        val ServerUnsupported = WalletDemoCredentialStatus("SERVER_UNSUPPORTED", "UNCERTAIN", "UNKNOWN", "NONE", "server-unsupported")
    }
}

@Serializable
data class WalletDemoStatusReference(val method: String, val purpose: String?, val failure: String? = null)

data class WalletDemoCredentialStatuses(
    val credentials: Map<String, WalletDemoCredentialStatus> = emptyMap(),
    val refreshing: Set<String> = emptySet(),
    val nextRefreshAt: String? = null,
)

data class WalletDemoStatusRefreshSummary(val completed: Int, val unchanged: Int, val deferred: Int, val failed: Int)

class WalletDemoStatusReviewChanged : IllegalStateException("Credential status changed. Review the updated information before sharing.")
