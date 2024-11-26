package id.walt.crypto.keys.azure

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType


interface AZUREKeyCreator {

    suspend fun generate(type: KeyType, keyName: String, metadata: AZUREKeyMetadata): Key
}