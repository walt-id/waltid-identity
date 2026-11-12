package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The current browser UI selects by credential ID; overlapping custom stores must remain explicit. */
internal fun CredentialStatusSnapshotDto.toDemoStatuses(): WalletDemoCredentialStatuses = WalletDemoCredentialStatuses(
    credentials = credentials.groupBy { it.reference.credentialId }.mapValues { (_, records) ->
        records.singleOrNull()?.presentation ?: WalletDemoCredentialStatus(
            kind = "NEEDS_REVIEW", issue = "NEEDS_REVIEW", freshness = "UNKNOWN", action = "NONE",
            advisoryRevision = "ambiguous-stored-credential-id", failure = "AMBIGUOUS_CREDENTIAL_ID",
        )
    },
    refreshing = refreshing.mapTo(mutableSetOf()) { it.credentialId },
    nextRefreshAt = nextRefreshAt,
)

internal val CredentialStatusRefreshResultDto.hasUnavailableChecks: Boolean
    get() = status["entries"]?.jsonArray?.any {
        it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "unavailable"
    } == true
