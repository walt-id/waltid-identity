package id.walt.crypto.keys.azure

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.security.keyvault.keys.KeyClient
import com.azure.security.keyvault.keys.KeyClientBuilder
import com.azure.security.keyvault.keys.cryptography.CryptographyAsyncClient
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder

internal object KeyVaultClientFactory {


    fun keyClient(vaultUrl: String): KeyClient =
        KeyClientBuilder()
            .vaultUrl(vaultUrl)
            .credential(DefaultAzureCredentialBuilder().build())
            .buildClient()


    fun cryptoClient(vaultUrl: String, keyName: String, version: String): CryptographyAsyncClient =
        CryptographyClientBuilder()
            .keyIdentifier("${vaultUrl.trimEnd('/')}/keys/$keyName/$version")
            .credential(DefaultAzureCredentialBuilder().build())
            .buildAsyncClient()
}
