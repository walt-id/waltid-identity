package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import id.walt.walletdemo.compose.logic.WalletDemoStatusReference

/** The current browser UI selects by credential ID; overlapping custom stores remain explicit. */
internal fun CredentialStatusSnapshotDto.toDemoStatuses(): WalletDemoCredentialStatuses = WalletDemoCredentialStatuses(
    credentials = credentials.groupBy { it.reference.credentialId }.mapValues { (_, records) ->
        records.singleOrNull()?.let { it.presentation.toDemoStatus().copy(storeIndex = it.reference.storeIndex) }
            ?: WalletDemoCredentialStatus.Ambiguous
    },
    refreshing = refreshing.mapTo(mutableSetOf()) { it.credentialId },
    nextRefreshAt = nextRefreshAt,
)

private fun CredentialStatusPresentationDto.toDemoStatus(): WalletDemoCredentialStatus = WalletDemoCredentialStatus(
    kind, issue, freshness, action, advisoryRevision, coverage, lastCheckedAt, lastAttemptAt, failure?.code, purposes,
    references.map { reference -> WalletDemoStatusReference(
        referenceId = reference.referenceId, protocolName = reference.protocolName, method = reference.method,
        purpose = reference.purpose, result = reference.result,
        checkedAt = reference.checkedAt, freshUntil = reference.freshUntil, validUntil = reference.validUntil,
        attemptedAt = reference.attemptedAt, failure = reference.failure?.code,
        conflict = reference.conflict, problem = reference.problem,
        eligibleAt = reference.failure?.eligibleAt, freshness = reference.freshness,
    ) }, lastKnown, discoveryProblem,
)

/** Applies immediate producer facts by the complete stored reference; no protocol interpretation. */
internal fun CredentialStatusSnapshotDto.withRefreshResults(results: List<CredentialStatusRefreshResultDto>): CredentialStatusSnapshotDto {
    val updates = results.associateBy { it.reference }
    require(updates.size == results.size && updates.keys.all { ref -> credentials.any { it.reference == ref } })
    val contexts = results.mapNotNull { it.statusContext }.distinct()
    require(contexts.size <= 1 && contexts.all { it.id == statusContext?.id })
    return copy(
        credentials = credentials.mapNotNull { record ->
            val update = updates[record.reference] ?: return@mapNotNull record
            if (update.outcome == "REMOVED") null else CredentialStatusRecordDto(record.reference, update.presentation)
        },
        refreshing = refreshing.filterNot { it in updates },
        statusContext = contexts.singleOrNull() ?: statusContext,
    )
}
