package id.walt.wallet2.mobile.credentials

import id.walt.credentials.status.CredentialStatus
import id.walt.credentials.status.CredentialStatusPresentation
import id.walt.credentials.status.assess
import kotlin.time.Instant

/** A composed read preserves the existing credential DTO and its caller metadata. */
public data class CredentialWithStatus(
    /** Existing credential and display metadata. */
    public val credential: WalletCredential,
    /** Per-reference observations, unsupported methods and failures. */
    public val status: CredentialStatus,
    /** Time at which this credential status was evaluated. */
    public val evaluatedAt: Instant,
) {
    /** Shared display meaning, freshness, coverage and semantic revision. */
    public val presentation: CredentialStatusPresentation get() = assessment.presentation

    private val assessment = status.assess(evaluatedAt)
}
