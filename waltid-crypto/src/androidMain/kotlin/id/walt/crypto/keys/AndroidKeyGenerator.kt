package id.walt.crypto.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import javax.security.auth.x500.X500Principal

object AndroidKeyGenerator : AndroidKeyCreator {

    private const val KEY_PAIR_ALIAS_PREFIX = "local_key_pair_"
    const val PUBLIC_KEY_ALIAS_PREFIX = "local_public_key_"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // Create an instance using key type
    override suspend fun generate(type: KeyType, metadata: JwkKeyMeta?): AndroidKey {
        val uniqueId = UUID.randomUUID().toString()
        val alias = "$KEY_PAIR_ALIAS_PREFIX$uniqueId"
        KeyPairGenerator.getInstance(getAlgorithmFor(type), ANDROID_KEYSTORE).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).run {

                    if (type == KeyType.secp256r1) {
                        setAlgorithmParameterSpec(ECGenParameterSpec(KeyType.secp256r1.name))
                    }

                    // Serial number used for the self-signed certificate of the generated key pair, default is 1
                    setCertificateSerialNumber(BigInteger.valueOf(777))

                    // Subject used for the self-signed certificate of the generated key pair, default is CN=fake
                    setCertificateSubject(X500Principal("CN=$alias"))

                    // Set of digests algorithms with which the key can be used
                    setDigests(
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512
                    )

                    // Set of padding schemes with which the key can be used when signing/verifying
                    setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)

                    setUserAuthenticationRequired(true)
                    setUserAuthenticationValidityDurationSeconds(5)

                    build()
                }
            )
        }.generateKeyPair()

        return AndroidKey(KeyAlias(alias), type)
    }

    private fun getAlgorithmFor(keyType: KeyType): String {
        return when (keyType) {
            KeyType.RSA -> KeyProperties.KEY_ALGORITHM_RSA
            KeyType.Ed25519 -> throw IllegalArgumentException("Ed25519 is not supported in Android KeyStore")
            KeyType.secp256k1 -> throw IllegalArgumentException("secp256k1 is not supported in Android KeyStore")
            KeyType.secp256r1 -> KeyProperties.KEY_ALGORITHM_EC
        }
    }
}