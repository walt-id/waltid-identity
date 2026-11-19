package id.walt.walletdemo.compose.ui.components

import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import id.walt.walletdemo.compose.logic.SharingCredentialInformation
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection

/** A selected record, including a missing assessment. Missing is never silently dropped. */
internal data class ReviewCredentialStatus(
    val credentialId: String,
    val title: String,
    val issuer: String?,
    val status: WalletDemoCredentialStatus?,
)

internal fun selectedReviewStatuses(
    information: List<SharingCredentialInformation>?,
    selected: Set<WalletDemoPresentationCredentialSelection>,
    snapshot: WalletDemoCredentialStatuses,
): List<ReviewCredentialStatus> = information.orEmpty().filter { it.option.selection in selected }
    .distinctBy { it.option.credentialId }
    .map { ReviewCredentialStatus(it.option.credentialId, it.summary.title, it.summary.issuer, snapshot.credentials[it.option.credentialId]) }

/** Null means unavailable binding data; an empty map is allowed only for explicit legacy hosts. */
internal fun List<ReviewCredentialStatus>.reviewedRevisions(snapshot: WalletDemoCredentialStatuses): Map<String, String>? {
    if (!snapshot.available) return null
    if (snapshot.legacySubmission) return emptyMap()
    if (any { it.status?.storeIndex == null || it.status.advisoryRevision.isBlank() }) return null
    return associate { it.credentialId to requireNotNull(it.status).advisoryRevision }
}
