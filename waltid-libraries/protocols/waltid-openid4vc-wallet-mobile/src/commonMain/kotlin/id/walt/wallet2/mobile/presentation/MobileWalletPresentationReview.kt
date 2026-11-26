package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.consent.PreparedPaymentConsent
import id.walt.wallet2.handlers.RetainedPresentationReview
import id.walt.wallet2.status.WalletCredentialStatus

/** Wallet-issued remote presentation review. Show its claims, selected advisories and any payment before submission. */
public class MobileWalletPresentationReview internal constructor(internal val retained: RetainedPresentationReview) {
    /** Effective disclosures, including any additional data the credential format carries. */
    public val claims: MobileWalletPreparedPresentationReview = retained.claims.toMobileReview()
    /** Local status of each selected credential at review time. */
    public val statuses: List<WalletCredentialStatus> get() = retained.status.credentials
    /** Authenticated payment terms for requests that require payment consent. */
    public val payment: PreparedPaymentConsent? get() = retained.payment
}
