package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.consent.PreparedPaymentConsent
import id.walt.wallet2.mobile.presentation.ReviewedPresentationClaims
import id.walt.wallet2.mobile.presentation.PresentationReview

/** Native transport for one wallet-owned review and its display values. */
public class WalletBridgePresentationReview internal constructor(internal val review: PresentationReview) {
    /** Effective disclosures rendered by the Swift host. */
    public val claims: ReviewedPresentationClaims get() = review.claims
    /** Selected status records encoded for the native Swift models. */
    public val statusJson: String get() = review.retained.status.toStatusBridgeSnapshot()
    /** Authenticated payment information for this selection, when required. */
    public val payment: PreparedPaymentConsent? get() = review.payment
}
