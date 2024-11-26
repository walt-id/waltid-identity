package id.walt.crypto.keys.azure

import id.walt.crypto.keys.KeyType


interface AZUREKeyCreator {

    suspend fun generate(type: KeyType, keyName: String? = "waltid", metadata: AZUREKeyMetadata): AZUREKEY
}