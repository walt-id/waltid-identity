package id.walt.crypto2.kms.azure.identity

import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import com.azure.identity.DefaultAzureCredentialBuilder
import id.walt.crypto2.kms.azure.AzureAccessTokenProvider
import id.walt.crypto2.kms.azure.AzureKeyVaultOptions
import kotlinx.coroutines.reactor.awaitSingle

class AzureIdentityTokenProvider(
    private val credential: TokenCredential = DefaultAzureCredentialBuilder().build(),
) : AzureAccessTokenProvider {
    override suspend fun getAccessToken(options: AzureKeyVaultOptions): String = credential
        .getToken(TokenRequestContext().addScopes(KEY_VAULT_SCOPE))
        .awaitSingle()
        .token
        .also { require(it.isNotBlank()) { "Azure Identity returned an empty access token" } }

    companion object {
        private const val KEY_VAULT_SCOPE = "https://vault.azure.net/.default"
    }
}
