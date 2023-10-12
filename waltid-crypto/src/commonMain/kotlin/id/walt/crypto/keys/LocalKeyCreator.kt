package id.walt.crypto.keys

interface LocalKeyCreator {
    suspend fun generate(type: KeyType, metadata: LocalKeyMetadata = LocalKeyMetadata()): LocalKey
    suspend fun importRawPublicKey(
        type: KeyType,
        rawPublicKey: ByteArray,
        metadata: LocalKeyMetadata
    ): Key

    suspend fun importJWK(jwk: String): Result<LocalKey>

    suspend fun importPEM(pem: String): Result<LocalKey>
}
