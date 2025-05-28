package id.walt.dcql

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Interface representing a credential held by the wallet.
 * Implementations will vary based on actual credential storage.
 */
interface DcqlCredential {
    val id: String // Wallet's internal unique ID for this credential
    val format: String // Matches CredentialQuery.format
    val data: JsonObject // The actual credential data (claims, etc.)
//    val issuer: String? // Issuer identifier (for trusted_authorities check)
    // Add other relevant properties like issuanceDate, expirationDate, etc. if needed

    val disclosures: List<DcqlDisclosure>?
}

data class DcqlDisclosure(
    val name: String,
    val value: JsonElement,

    // A place to pass a reference to the original (using which this class was created)
    //val original: Any? = null // no longer needed?
)

data class RawDcqlCredential(
    override val id: String,
    override val format: String,
    override val data: JsonObject,
    override val disclosures: List<DcqlDisclosure>? = null,

    // A place to pass a reference to the original (using which this class was created)
    val originalCredential: Any? = null
//    override val issuer: String? = null,
) : DcqlCredential
