package id.walt.wallet2.data

import id.walt.credentials.formats.DigitalCredential
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * A credential stored in a wallet, wrapping the fully-parsed [id.walt.credentials.formats.DigitalCredential]
 * with wallet-assigned metadata.
 *
 * Using [id.walt.credentials.formats.DigitalCredential] directly avoids raw string handling and gives
 * callers access to typed format, issuer, subject, credentialData, and
 * selective-disclosure support without re-parsing.
 */
@Serializable
data class StoredCredential(
    /** Wallet-assigned identifier (UUID). Not the credential's own `id` field. */
    val id: String,
    val credential: DigitalCredential,
    /** Optional human-readable label set by the user or derived from the credential. */
    val label: String? = null,
    /** When the credential was added to this wallet. */
    val addedAt: Instant? = null,
    /** Optional arbitrary metadata to store alongside the credential (e.g., issuer info, policy results). */
    val metadata: JsonObject? = null,
) {
    /** Returns a metadata-only view of this credential (no raw encoded data). */
    fun toMetadata() = StoredCredentialMetadata(
        id = id,
        format = credential.format,
        issuer = credential.issuer,
        subject = credential.subject,
        label = label,
        addedAt = addedAt
    )
}
