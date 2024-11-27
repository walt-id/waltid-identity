package id.walt.crypto.keys.azure

import kotlinx.serialization.Serializable


@Serializable
data class AzureKeyMetadata(
    val auth: AzureAuth,
) {
    constructor(
        clientId: String,
        clientSecret: String,
        keyVaultUrl: String,
        tenantId: String,
        keyName: String = "waltid"
    ) : this(AzureAuth(clientId, clientSecret, tenantId, keyVaultUrl))
}

