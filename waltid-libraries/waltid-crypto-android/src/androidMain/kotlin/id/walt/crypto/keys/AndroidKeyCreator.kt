package id.walt.crypto.keys

interface AndroidKeyCreator {
    suspend fun generate(type: KeyType, metadata: JwkKeyMeta? = null): AndroidKey
}