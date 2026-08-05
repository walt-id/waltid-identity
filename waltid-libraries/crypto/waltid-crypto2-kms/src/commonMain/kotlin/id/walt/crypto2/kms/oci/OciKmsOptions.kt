package id.walt.crypto2.kms.oci

import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.requireHttpEndpoint
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class OciKmsOptions(
    val managementEndpoint: String,
    val cryptoEndpoint: String,
    val compartmentOcid: String,
    val credentialReference: CredentialReference,
    val protectionMode: OciProtectionMode = OciProtectionMode.SOFTWARE,
) {
    init {
        requireHttpEndpoint(managementEndpoint, "OCI management endpoint")
        requireHttpEndpoint(cryptoEndpoint, "OCI crypto endpoint")
        require(compartmentOcid.isNotBlank()) { "OCI compartment OCID cannot be blank" }
    }

    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

        internal fun decode(data: BinaryData): OciKmsOptions = json.decodeFromString(data.toByteArray().decodeToString())

    }
}

@Serializable
enum class OciProtectionMode {
    SOFTWARE,
    HSM,
}

class OciApiKeyCredential(
    val tenancyOcid: String,
    val userOcid: String,
    val fingerprint: String,
    val privateKeyPem: String,
) {
    init {
        require(tenancyOcid.isNotBlank()) { "OCI tenancy OCID cannot be blank" }
        require(userOcid.isNotBlank()) { "OCI user OCID cannot be blank" }
        require(fingerprint.isNotBlank()) { "OCI key fingerprint cannot be blank" }
        require(privateKeyPem.isNotBlank()) { "OCI API private key cannot be blank" }
    }

    val keyId: String
        get() = "$tenancyOcid/$userOcid/$fingerprint"
}

fun interface OciCredentialResolver {
    suspend fun resolve(reference: CredentialReference): OciApiKeyCredential
}
