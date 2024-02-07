package id.walt.crypto.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.UUID
import javax.security.auth.x500.X500Principal

object AndroidLocalKeyGenerator : LocalKeyCreator {

    private const val KEY_PAIR_ALIAS_PREFIX = "local_key_pair_"
    const val PUBLIC_KEY_ALIAS_PREFIX = "local_public_key_"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // Create an instance using key type
    override suspend fun generate(type: KeyType, metadata: LocalKeyMetadata): LocalKey {
        val uniqueId = UUID.randomUUID().toString()
        val alias = "$KEY_PAIR_ALIAS_PREFIX$uniqueId"
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).run {
                    // Serial number used for the self-signed certificate of the generated key pair, default is 1
                    setCertificateSerialNumber(BigInteger.valueOf(777))

                    // Subject used for the self-signed certificate of the generated key pair, default is CN=fake
                    setCertificateSubject(X500Principal("CN=$alias"))

                    // Set of digests algorithms with which the key can be used
                    setDigests(KeyProperties.DIGEST_SHA256)

                    // Set of padding schemes with which the key can be used when signing/verifying
                    setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)

                    setUserAuthenticationRequired(true)
                    setUserAuthenticationValidityDurationSeconds(5)

                    build()
                }
            )
        }.generateKeyPair()

        return LocalKey(KeyAlias(alias))
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