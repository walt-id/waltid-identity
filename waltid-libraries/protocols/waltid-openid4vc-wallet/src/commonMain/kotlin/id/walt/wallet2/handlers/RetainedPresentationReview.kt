package id.walt.wallet2.handlers

import id.walt.wallet2.consent.PreparedPaymentConsent
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.status.WalletCredentialStatusSnapshot
import id.waltid.openid4vp.wallet.presentation.PresentationReviewGuard

/** Wallet-issued disclosure review, bound to its selected credentials, holder, advisories and payment. */
class RetainedPresentationReview internal constructor(
    val claims: PreparedPresentationReview,
    val status: WalletCredentialStatusSnapshot,
    val payment: PreparedPaymentConsent?,
    internal val owner: Wallet,
    internal val request: SubmitPresentationRequest,
    internal val guard: PresentationReviewGuard,
)

/** The selected credentials or holder changed while preparing to share. Review the current selection again. */
class PresentationReviewChangedException : IllegalStateException("Presentation changed. Review the updated selection before sharing.")
