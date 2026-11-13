package id.walt.wallet2.mobile

import id.walt.credentials.status.CredentialStatus
import id.walt.credentials.status.CredentialStatusPresentation
import id.walt.credentials.status.CredentialStatusPresentationData
import id.walt.credentials.status.assess
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** A composed read preserves the existing credential DTO and its caller metadata. */
public data class MobileWalletCredentialRead(
    /** Existing credential and display metadata. */
    public val credential: MobileWalletCredential,
    /** Per-reference observations, unsupported methods and failures. */
    public val status: CredentialStatus,
    /** ISO-8601 time used to evaluate this presentation. */
    public val evaluatedAt: String,
) {
    private val assessment = status.assess(Instant.parse(evaluatedAt))
    /** Shared display meaning, freshness, coverage and semantic revision. */
    public val presentation: CredentialStatusPresentation get() = assessment.presentation
}

/** Wire representation for native adapters; status values are interpreted in shared Kotlin. */
@Serializable
public data class MobileWalletCredentialStatusValue(
    /** Per-reference observations, unsupported methods and failures. */
    public val status: CredentialStatus,
    /** Shared display meaning, freshness, coverage and semantic revision. */
    public val presentation: CredentialStatusPresentationData,
    /** ISO-8601 time used to evaluate this presentation. */
    public val evaluatedAt: String,
)
