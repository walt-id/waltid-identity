package id.walt.crypto.keys.oci

import kotlinx.serialization.Serializable

@Serializable
data class OCIKeyMetadata(
    val tenancyOcid: String,
    val compartmentOcid: String,
    val userOcid: String,
    val fingerprint: String,
    val managementEndpoint: String,
    val cryptoEndpoint: String,
    val signingKeyPem: String? = null
)
