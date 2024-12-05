package id.walt.crypto.keys.azure

import kotlinx.serialization.Serializable
import kotlin.random.Random


@Serializable
data class AzureKeyMetadata(
    val auth: AzureAuth,
    val name: String? = null
) {
    constructor(
        clientId: String,
        clientSecret: String,
        keyVaultUrl: String,
        tenantId: String,
        name: String = Random.nextInt().toString()
    ) : this(AzureAuth(clientId, clientSecret, tenantId, keyVaultUrl), name = name)
}

