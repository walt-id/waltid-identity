package id.walt.crypto.keys

interface AndroidKeyCreator {
    suspend fun generate(type: KeyType, metadata: LocalKeyMetadata = LocalKeyMetadata()): AndroidKey
}