package id.walt.crypto.keys.azure

import id.walt.crypto.keys.KeyType


interface AzureKeyCreator {

    suspend fun generate(type: KeyType, metadata: AzureKeyMetadata): AzureKeyRestApi
}
