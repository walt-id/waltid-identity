package id.walt.crypto.keys

interface AndroidKeyLoader {
    suspend fun load(type: KeyType, keyId: String): AndroidKey?
}