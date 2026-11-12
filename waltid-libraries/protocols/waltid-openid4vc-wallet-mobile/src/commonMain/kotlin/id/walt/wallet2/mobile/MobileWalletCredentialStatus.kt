package id.walt.wallet2.mobile

import id.walt.credentials.status.CredentialStatusMetadata
import id.walt.credentials.status.CredentialStatusPresentation
import kotlinx.serialization.Serializable

/** A composed read preserves the existing credential DTO and its caller metadata. */
public data class MobileWalletCredentialRead(
    /** Existing credential and display metadata. */
    public val credential: MobileWalletCredential,
    /** Per-reference observations, unsupported methods and failures. */
    public val status: CredentialStatusMetadata,
    /** Shared display meaning, freshness, coverage and semantic revision. */
    public val presentation: CredentialStatusPresentation,
    /** ISO-8601 time used to evaluate this presentation. */
    public val evaluatedAt: String,
)

/** Wire representation for native adapters; status values are interpreted in shared Kotlin. */
@Serializable
public data class MobileWalletCredentialStatusValue(
    /** Per-reference observations, unsupported methods and failures. */
    public val status: CredentialStatusMetadata,
    /** Shared display meaning, freshness, coverage and semantic revision. */
    public val presentation: CredentialStatusPresentation,
    /** ISO-8601 time used to evaluate this presentation. */
    public val evaluatedAt: String,
)
