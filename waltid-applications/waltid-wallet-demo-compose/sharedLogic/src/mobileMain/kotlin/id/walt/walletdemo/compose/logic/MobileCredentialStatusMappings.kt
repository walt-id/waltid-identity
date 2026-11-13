package id.walt.walletdemo.compose.logic

import id.walt.credentials.status.code
import id.walt.credentials.status.hasCheckError
import id.walt.credentials.status.CheckFailure
import id.walt.credentials.status.CredentialStatusPresentation
import id.walt.wallet2.status.CredentialStatusRefreshOutcome
import id.walt.wallet2.status.CredentialStatusRefreshResult
import id.walt.wallet2.status.WalletCredentialStatus
import id.walt.wallet2.status.WalletCredentialStatusSnapshot

fun WalletCredentialStatusSnapshot.toDemoStatuses(): WalletDemoCredentialStatuses = WalletDemoCredentialStatuses(
    credentials.groupBy { it.reference.credentialId }.mapValues { (_, records) ->
        records.singleOrNull()?.toDemoStatus() ?: WalletDemoCredentialStatus.Ambiguous
    },
    refreshing.mapTo(mutableSetOf()) { it.credentialId },
    nextRefreshAt?.toString(),
)

internal fun WalletCredentialStatus.toDemoStatus(): WalletDemoCredentialStatus =
    presentation.toDemoStatus().copy(storeIndex = reference.storeIndex)

fun CredentialStatusPresentation.toDemoStatus(): WalletDemoCredentialStatus = let {
    WalletDemoCredentialStatus(it.kind.name, it.issue.name, it.freshness.name, it.action.name, it.advisoryRevision,
        it.coverage.name, it.lastCheckedAt?.toString(), it.lastAttemptAt?.toString(), it.failure?.code, it.purposes,
        it.references.map { reference -> WalletDemoStatusReference(
            referenceId = reference.referenceId, protocolName = reference.protocol?.name, method = reference.method,
            purpose = reference.purpose, result = reference.result?.name,
            checkedAt = reference.checkedAt?.toString(), freshUntil = reference.freshUntil?.toString(), validUntil = reference.validUntil?.toString(),
            attemptedAt = reference.attemptedAt?.toString(), failure = reference.failure?.code,
            conflict = reference.conflict, problem = reference.problem?.name,
            eligibleAt = when (val failure = reference.failure) {
                is CheckFailure.NotYetValid -> failure.validFrom.toString()
                is CheckFailure.RateLimited -> failure.retryAt?.toString()
                else -> null
            },
            freshness = reference.freshness.name,
        ) }, it.lastKnown, it.discoveryProblem?.name)
}

internal fun List<CredentialStatusRefreshResult>.toDemoSummary(): WalletDemoStatusRefreshSummary = WalletDemoStatusRefreshSummary(
    count { it.outcome == CredentialStatusRefreshOutcome.COMPLETED },
    count { it.outcome == CredentialStatusRefreshOutcome.UNCHANGED },
    count { it.outcome == CredentialStatusRefreshOutcome.DEFERRED },
    count { it.outcome != CredentialStatusRefreshOutcome.REMOVED && it.status.hasCheckError },
)
