package id.walt.wallet2.data

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Metadata-only projection of a [StoredCredential] for list endpoints.
 * Does not include raw credential data or credentialData payload.
 */
@Serializable
data class StoredCredentialMetadata(
    val id: String,
    val format: String,
    val issuer: String? = null,
    val subject: String? = null,
    val label: String? = null,
    val addedAt: Instant? = null
)
