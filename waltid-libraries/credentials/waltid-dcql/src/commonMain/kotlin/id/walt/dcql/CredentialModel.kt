package id.walt.dcql

import kotlinx.serialization.json.JsonObject

/**
 * Interface representing a credential held by the wallet.
 * Implementations will vary based on actual credential storage.
 */
interface Credential {
    val id: String // Wallet's internal unique ID for this credential
    val format: String // Matches CredentialQuery.format
    val data: JsonObject // The actual credential data (claims, etc.)
//    val issuer: String? // Issuer identifier (for trusted_authorities check)
    // Add other relevant properties like issuanceDate, expirationDate, etc. if needed
}

/**
 * A sample concrete implementation for testing purposes.
 */
data class SampleCredential(
    override val id: String,
    override val format: String,
    override val data: JsonObject,
//    override val issuer: String? = null,
) : Credential
