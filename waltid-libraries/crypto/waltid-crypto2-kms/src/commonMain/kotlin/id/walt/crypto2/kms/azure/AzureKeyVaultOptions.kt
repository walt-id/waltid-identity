package id.walt.crypto2.kms.azure

import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.requireHttpEndpoint
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AzureKeyVaultOptions(
    val keyVaultUrl: String,
    val tenantId: String,
    val credentialReference: CredentialReference,
    val keyName: String? = null,
    val authorityUrl: String = "https://login.microsoftonline.com",
) {
    init {
        requireHttpEndpoint(keyVaultUrl, "Azure Key Vault URL")
        requireHttpEndpoint(authorityUrl, "Azure authority URL")
        require(tenantId.isNotBlank()) { "Azure tenant ID cannot be blank" }
        require(keyName == null || keyName.isNotBlank()) { "Azure key name cannot be blank" }
    }

    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }

        internal fun decode(data: BinaryData): AzureKeyVaultOptions =
            json.decodeFromString(data.toByteArray().decodeToString())
    }
}

class AzureClientSecretCredential(
    val clientId: String,
    val clientSecret: String,
) {
    init {
        require(clientId.isNotBlank()) { "Azure client ID cannot be blank" }
        require(clientSecret.isNotBlank()) { "Azure client secret cannot be blank" }
    }
}

fun interface AzureCredentialResolver {
    suspend fun resolve(reference: CredentialReference): AzureClientSecretCredential
}
