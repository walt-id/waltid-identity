package id.walt.crypto.keys.azure

import kotlinx.serialization.Serializable


@Serializable
data class AzureKeyMetadataSDK(
    val vaultUrl: String,
)
