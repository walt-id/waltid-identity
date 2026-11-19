package id.walt.itb

import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.status.CredentialStatusCoverage
import id.walt.credentials.status.CredentialStatusFreshness
import id.walt.credentials.status.CredentialStatusKind
import id.walt.credentials.status.StatusProtocol
import id.walt.credentials.status.code
import id.walt.policies2.vc.policies.status.model.IETFEntry
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshOutcome
import id.walt.wallet2.status.WalletCredentialStatusService
import kotlinx.serialization.json.Json

enum class ItbRevocationStep { FIRST_SHARED, SECOND_SHARED, ALREADY_REVOKED }

/** Check the credentials issued by this session through the wallet's ordinary status read and refresh APIs. */
internal suspend fun checkRevocationStatus(
    status: WalletCredentialStatusService,
    ids: List<String>,
    expected: CredentialStatusKind,
) {
    if (ids.distinct().size != ids.size) throw ItbWalletRejection("status_expected_distinct_credentials")
    val credentials = ids.map { id ->
        status.readCredential(id) ?: throw ItbWalletRejection("status_credential_not_stored")
    }
    val references = credentials.map { read ->
        val credential = read.credential.credential as? SdJwtCredential
            ?: throw ItbWalletRejection("status_expected_sd_jwt")
        val claim = credential.originalCredentialData?.get("status")
            ?: throw ItbWalletRejection("status_missing_list_reference")
        Json.decodeFromJsonElement(IETFEntry.serializer(), claim)
    }
    if (references.size == 2) {
        val (first, second) = references
        if (first.uri != second.uri || first.index == second.index) {
            throw ItbWalletRejection("status_expected_shared_list_with_distinct_indices")
        }
    }
    val updates = status.refresh(credentials.map { it.reference }.toSet(), CredentialStatusRefreshMode.FORCE)
    if (updates.any { it.outcome != CredentialStatusRefreshOutcome.COMPLETED }) {
        throw ItbWalletRejection("status_refresh_incomplete")
    }
    for (id in ids) {
        val observed = status.readCredential(id)?.presentation
            ?: throw ItbWalletRejection("status_credential_not_stored")
        if (observed.references.singleOrNull()?.protocol != StatusProtocol.TOKEN_STATUS_LIST ||
            observed.coverage != CredentialStatusCoverage.COMPLETE ||
            observed.freshness != CredentialStatusFreshness.FRESH ||
            observed.kind != expected
        ) {
            val reason = observed.failure?.code ?: observed.kind.name
            throw ItbWalletRejection("status_${reason.lowercase()}")
        }
    }
}
