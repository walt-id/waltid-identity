package id.walt.crypto.keys

/*
interface KeyCreator {
    /**
     * generate a key
     * @param type the type of key to generate
     * @param metadata key options
     * @return generated key
     */
    suspend fun generate(type: KeyType, arguments: Map<String, Any>): Key

    suspend fun importRawPublicKey(type: KeyType, rawPublicKey: ByteArray, arguments: Map<String, Any>): Key

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
*/
