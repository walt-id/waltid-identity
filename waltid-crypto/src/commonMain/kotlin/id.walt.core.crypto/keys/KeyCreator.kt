package id.walt.core.crypto.keys

interface KeyCreator {
    /**
     * generate a key
     * @param type the type of key to generate
     * @param metadata key algorithm options or JWK header fields
     * @return generated key
     */
    suspend fun generate(type: KeyType, metadata: KeyMetadata = KeyMetadata()): Key

    suspend fun importRawPublicKey(type: KeyType, metadata: KeyMetadata, rawPublicKey: ByteArray): Key

    /**
     * import a key from an encoded JWK
     * @param jwk encoded JWK
     * @return imported key
     */
    suspend fun importJWK(jwk: String): Result<Key>
    /**
     * import a key from an encoded PEM
     * @param pem encoded PEM
     * @return imported key
     */
    suspend fun importPEM(pem: String): Result<Key>
}
