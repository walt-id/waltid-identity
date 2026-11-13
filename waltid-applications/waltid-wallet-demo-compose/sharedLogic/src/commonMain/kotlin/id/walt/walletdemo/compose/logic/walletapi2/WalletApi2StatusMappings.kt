package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import id.walt.walletdemo.compose.logic.WalletDemoStatusReference
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

/** Count returned records with check errors; this overlaps the batch admission/completion counts. */
internal val CredentialStatusRefreshResultDto.hasCheckErrors: Boolean
    get() = when (status["type"]?.jsonPrimitive?.contentOrNull) {
        "discovery_failed" -> true
        "references" -> requireNotNull(status["entries"]).jsonArray.any {
            val entry = it.jsonObject
            when (entry["type"]?.jsonPrimitive?.contentOrNull) {
                "malformed" -> true
                "unsupported" -> false
                "token", "mso", "bitstring", "legacy" -> when (entry["check"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull) {
                    "failed", "conflict" -> true
                    "observed", "not_checked" -> false
                    else -> error("Invalid credential status check")
                }
                else -> error("Invalid credential status entry")
            }
        }
        else -> error("Invalid credential status discovery result")
    }
