package id.walt.crypto.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import javax.security.auth.x500.X500Principal

object AndroidLocalKeyGenerator : LocalKeyCreator {

    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "custom_key_alias"

    // Create an instance using key type
    override suspend fun generate(type: KeyType, metadata: LocalKeyMetadata): LocalKey {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).run {
                    setCertificateSerialNumber(BigInteger.valueOf(777))       //Serial number used for the self-signed certificate of the generated key pair, default is 1
                    setCertificateSubject(X500Principal("CN=$KEY_ALIAS"))     //Subject used for the self-signed certificate of the generated key pair, default is CN=fake
                    setDigests(KeyProperties.DIGEST_SHA256)                         //Set of digests algorithms with which the key can be used
                    setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1) //Set of padding schemes with which the key can be used when signing/verifying
                    build()
                }
            )
        }.generateKeyPair()
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