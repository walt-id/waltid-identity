package id.walt.walletdemo.compose.logic

import id.walt.credentials.status.*
import id.walt.wallet2.status.*

fun WalletCredentialStatusSnapshot.toDemoStatuses(): WalletDemoCredentialStatuses = WalletDemoCredentialStatuses(
    credentials.associate { entry -> entry.reference.credentialId to entry.toDemoStatus() },
    refreshing.mapTo(mutableSetOf()) { it.credentialId },
    nextRefreshAt?.toString(),
)

internal fun WalletCredentialStatus.toDemoStatus(): WalletDemoCredentialStatus = presentation.let {
    WalletDemoCredentialStatus(it.kind.name, it.issue.name, it.freshness.name, it.action.name, it.advisoryRevision,
        it.coverage.name, it.lastCheckedAt?.toString(), it.lastAttemptAt?.toString(), it.failure?.name, it.purposes,
        status.entries.map { entry -> when (entry) {
            is CredentialStatusEntry.NotChecked -> WalletDemoStatusReference(entry.protocol.name, entry.purpose)
            is CredentialStatusEntry.Unsupported -> WalletDemoStatusReference(entry.method, null, "UNSUPPORTED_METHOD")
            is CredentialStatusEntry.Observed -> WalletDemoStatusReference(entry.observation.protocol.name, entry.observation.purpose)
            is CredentialStatusEntry.Unavailable -> WalletDemoStatusReference(entry.previous?.protocol?.name ?: "Status reference", entry.previous?.purpose, entry.reason.name)
        } })
}

internal fun List<CredentialStatusRefreshResult>.toDemoSummary(): WalletDemoStatusRefreshSummary = WalletDemoStatusRefreshSummary(
    count { it.outcome == CredentialStatusRefreshOutcome.COMPLETED },
    count { it.outcome == CredentialStatusRefreshOutcome.UNCHANGED },
    count { it.outcome == CredentialStatusRefreshOutcome.DEFERRED },
    count { it.status.entries.any { entry -> entry is CredentialStatusEntry.Unavailable } },
)
