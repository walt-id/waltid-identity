package id.walt.wallet2.status

import id.waltid.openid4vp.wallet.presentation.PresentationReviewGuard

/** The selected credential's semantic warning changed since the host rendered its confirmation. */
class CredentialStatusReviewChangedException : IllegalStateException("Credential status changed. Review the updated information before sharing.")

/** A local comparison only; timestamps are excluded from the shared semantic revision. */
fun WalletCredentialStatusService.reviewGuard(selectedIds: Set<String>, reviewed: Map<String, String>): PresentationReviewGuard =
    PresentationReviewGuard {
        val current = snapshot().credentials
        if (selectedIds.any { id ->
                val records = current.filter { it.reference.credentialId == id }
                records.size != 1 || records.single().presentation.advisoryRevision != reviewed[id]
            }) throw CredentialStatusReviewChangedException()
    }
