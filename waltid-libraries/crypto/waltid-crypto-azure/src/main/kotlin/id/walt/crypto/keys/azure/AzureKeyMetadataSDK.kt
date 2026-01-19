package id.walt.crypto.keys.azure

import kotlinx.serialization.Serializable


@Serializable
data class AzureKeyMetadataSDK(
    val auth: AzureSDKAuth,
    val keyName: String? = null,
    val tags: Map<String, String>? = null,
)

@Serializable
data class AzureSDKAuth(
    val keyVaultUrl: String,
)