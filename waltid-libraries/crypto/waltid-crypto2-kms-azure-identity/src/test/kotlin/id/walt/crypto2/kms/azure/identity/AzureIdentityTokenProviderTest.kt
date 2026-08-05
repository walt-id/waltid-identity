package id.walt.crypto2.kms.azure.identity

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.azure.AzureKeyVaultOptions
import kotlinx.coroutines.test.runTest
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AzureIdentityTokenProviderTest {
    @Test
    fun `requests the Key Vault scope from injected Azure credential`() = runTest {
        var scopes = emptyList<String>()
        val credential = TokenCredential { context: TokenRequestContext ->
            scopes = context.scopes
            Mono.just(AccessToken("managed-token", OffsetDateTime.now().plusHours(1)))
        }
        val provider = AzureIdentityTokenProvider(credential)

        val token = provider.getAccessToken(
            AzureKeyVaultOptions(
                keyVaultUrl = "https://vault.example",
                tenantId = "tenant",
                credentialReference = CredentialReference("unused-by-identity"),
            )
        )

        assertEquals("managed-token", token)
        assertEquals(listOf("https://vault.azure.net/.default"), scopes)
    }
}
