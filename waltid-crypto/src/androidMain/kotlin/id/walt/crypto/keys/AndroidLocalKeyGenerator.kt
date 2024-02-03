package id.walt.crypto.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator

object AndroidLocalKeyGenerator : LocalKeyCreator {

    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    const val KEY_ALIAS = "custom_key_alias"

    // Create an instance using key type
    override suspend fun generate(type: KeyType, metadata: LocalKeyMetadata): LocalKey {
        KeyGenerator.getInstance(ALGORITHM).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
        return LocalKey(null)
    }

    // create an instance using keytype AND a public key
    override suspend fun importRawPublicKey(type: KeyType, rawPublicKey: ByteArray, metadata: LocalKeyMetadata): Key {
        TODO("Not yet implemented")
    }

    // create an instance using JWK string
    override suspend fun importJWK(jwk: String): Result<LocalKey> {
        TODO("Not yet implemented")
    }

    // create an instance using PEM string
    override suspend fun importPEM(pem: String): Result<LocalKey> {
        TODO("Not yet implemented")
    }
}