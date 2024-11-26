package id.walt.crypto.keys.azure

import kotlinx.serialization.Serializable


@Serializable
data class AZUREKeyMetadata(
    val auth: AZUREAuth,
) {
    constructor(
        clientId: String,
        clientSecret: String,
        keyVaultUrl: String,
        tenantId: String,
    ) : this(AZUREAuth(clientId, clientSecret, tenantId, keyVaultUrl))
}

